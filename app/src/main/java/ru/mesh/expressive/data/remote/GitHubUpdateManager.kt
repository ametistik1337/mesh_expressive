package ru.mesh.expressive.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.mesh.expressive.BuildConfig
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?
)

class GitHubUpdateManager {

    companion object {
        private const val GITHUB_REPO = "ametistik1337/mesh_expressive"
        private const val COUNTER_PING_URL = "http://204.77.3.91/mesh-counter/ping"
    }

    suspend fun checkForUpdates(): GitHubReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "MeshExpressive-Android")
                setRequestProperty("Accept", "application/vnd.github+json")
            }

            if (connection.responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val tagName = json.optString("tag_name", "")
                val name = json.optString("name", tagName)
                val body = json.optString("body", "")
                val htmlUrl = json.optString("html_url", "https://github.com/$GITHUB_REPO/releases/latest")

                var apkDownloadUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else null
                            break
                        }
                    }
                }

                val currentVersion = BuildConfig.VERSION_NAME
                if (isNewerVersion(tagName, currentVersion)) {
                    return@withContext GitHubReleaseInfo(
                        tagName = tagName,
                        name = name,
                        body = body,
                        htmlUrl = htmlUrl,
                        apkDownloadUrl = apkDownloadUrl
                    )
                }
            }
        } catch (_: Exception) {}
        null
    }

    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        try {
            val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")
            val rParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
            val cParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {}
        return false
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        tagName: String,
        onProgress: (Float) -> Unit
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = okhttp3.Request.Builder()
                .url(downloadUrl)
                .addHeader("User-Agent", "MeshExpressive-Android")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
            }
            val body = resp.body ?: return@withContext Result.failure(IllegalStateException("Пустой ответ"))
            val totalBytes = body.contentLength()
            val safeTag = tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val outFile = java.io.File(context.cacheDir, "mesh_update_$safeTag.apk")
            if (outFile.exists()) outFile.delete()

            body.byteStream().use { input ->
                outFile.outputStream().use { output: java.io.FileOutputStream ->
                    val buf = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        if (totalBytes > 0L) {
                            onProgress((downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
            onProgress(1f)
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncActiveCounter(
        installId: String,
        vacationDates: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val vacArray = JSONArray()
            vacationDates.filter { it.length == 10 }.forEach { vacArray.put(it) }

            val payload = JSONObject().apply {
                put("install_id", installId.take(12))
                put("vacations", vacArray)
            }

            val url = URL(COUNTER_PING_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("User-Agent", "MeshExpressive-Android")
                setRequestProperty("Content-Type", "application/json")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            return@withContext conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }
}
