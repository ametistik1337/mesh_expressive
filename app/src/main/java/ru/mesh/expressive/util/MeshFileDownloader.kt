package ru.mesh.expressive.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.mesh.expressive.data.local.SessionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class DownloadResult {
    data class Success(val uri: Uri, val fileName: String, val mimeType: String) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

object MeshFileDownloader {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "odt" -> "application/vnd.oasis.opendocument.text"
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "txt" -> "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        }
    }

    private fun getStorageDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "mesh_attachments")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getAttachmentFile(context: Context, fileName: String): File {
        val safeName = fileName.replace(Regex("[/\\\\:?*\"<>|]"), "_")
        return File(getStorageDir(context), safeName)
    }

    fun isDownloaded(context: Context, fileName: String): Boolean {
        val file = getAttachmentFile(context, fileName)
        return file.exists() && file.length() > 0
    }

    fun getDownloadedFileNames(context: Context): Set<String> {
        val dir = getStorageDir(context)
        if (!dir.exists()) return emptySet()
        return dir.listFiles()?.filter { it.isFile && it.length() > 0 }?.map { it.name }?.toSet().orEmpty()
    }

    fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun downloadFile(
        context: Context,
        url: String,
        suggestedFileName: String,
        sessionManager: SessionManager? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext DownloadResult.Error("Ссылка на файл пуста")
        }

        try {
            val fullUrl = when {
                url.startsWith("http://") || url.startsWith("https://") -> url
                url.startsWith("/") -> "https://school.mos.ru$url"
                else -> "https://school.mos.ru/$url"
            }

            val requestBuilder = Request.Builder().url(fullUrl)

            // Если запрос к экосистеме mos.ru, подставим авторизационные токены
            if (fullUrl.contains("mos.ru") && sessionManager != null) {
                val token = sessionManager.authToken
                if (!token.isNullOrBlank()) {
                    val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                    requestBuilder.addHeader("Authorization", bearer)
                }
                val profId = sessionManager.profileId
                if (profId.isNotBlank()) {
                    requestBuilder.addHeader("Profile-Id", profId)
                }
                requestBuilder.addHeader("x-mes-subsystem", "familymp")
                requestBuilder.addHeader("client-type", "diary-mobile")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext DownloadResult.Error("Сервер вернул ошибку: ${response.code}")
            }

            val responseBody = response.body ?: return@withContext DownloadResult.Error("Тело ответа пустое")

            // Извлечение имени файла из Content-Disposition или URL
            var resolvedName = suggestedFileName.trim()
            val disposition = response.header("Content-Disposition")
            if (disposition != null) {
                val utf8Match = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(disposition)
                if (utf8Match != null) {
                    resolvedName = java.net.URLDecoder.decode(utf8Match.groupValues[1], "UTF-8")
                } else {
                    val plainMatch = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(disposition)
                    if (plainMatch != null) {
                        resolvedName = plainMatch.groupValues[1]
                    }
                }
            }

            if (resolvedName.isBlank() || resolvedName == "Файл") {
                val pathSegment = Uri.parse(fullUrl).lastPathSegment
                if (!pathSegment.isNullOrBlank()) {
                    resolvedName = pathSegment
                }
            }
            if (resolvedName.isBlank()) {
                resolvedName = "mesh_file_${System.currentTimeMillis()}"
            }

            val mimeType = getMimeType(resolvedName)

            // 1. Сохраняем в локальную директорию приложения
            val localFile = getAttachmentFile(context, resolvedName)
            responseBody.byteStream().use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 2. Также дублируем в системные Загрузки (MediaStore / Public Downloads)
            try {
                copyToPublicDownloads(context, localFile, resolvedName, mimeType)
            } catch (_: Exception) {}

            val savedUri = getFileUri(context, localFile)
            DownloadResult.Success(savedUri, resolvedName, mimeType)
        } catch (e: Exception) {
            DownloadResult.Error(e.localizedMessage ?: "Ошибка скачивания файла")
        }
    }

    private fun copyToPublicDownloads(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return

            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            var dest = File(downloadsDir, fileName)
            var counter = 1
            val base = fileName.substringBeforeLast('.')
            val ext = fileName.substringAfterLast('.', "")
            while (dest.exists()) {
                val newName = if (ext.isNotBlank()) "${base}_$counter.$ext" else "${base}_$counter"
                dest = File(downloadsDir, newName)
                counter++
            }

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    fun openFile(context: Context, uri: Uri, mimeType: String, title: String = "Открыть файл") {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "Не найдено приложение для открытия файла", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, fileName: String) {
        try {
            val localFile = getAttachmentFile(context, fileName)
            if (!localFile.exists()) return
            val uri = getFileUri(context, localFile)
            val mimeType = getMimeType(fileName)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Поделиться файлом").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось поделиться файлом", Toast.LENGTH_SHORT).show()
        }
    }
}
