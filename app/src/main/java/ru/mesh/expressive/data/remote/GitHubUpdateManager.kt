package ru.mesh.expressive.data.remote

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.mesh.expressive.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

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
        private const val GIST_ID = "69d25f42e4922632adb85464e0aa30e1"
        // Token strictly for Gist stats counter (kept outside user personal servers)
        private val GIST_TOKEN by lazy {
            try {
                String(Base64.decode("", Base64.DEFAULT)).trim()
            } catch (_: Exception) {
                ""
            }
        }
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
        if (GIST_TOKEN.isBlank()) return@withContext false
        try {
            // 1. Fetch current gist content
            val getUrl = URL("https://api.github.com/gists/$GIST_ID")
            val getConn = (getUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Authorization", "Bearer $GIST_TOKEN")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MeshExpressive-Android")
            }

            if (getConn.responseCode !in 200..299) return@withContext false
            val getResponseText = getConn.inputStream.bufferedReader().use { it.readText() }
            val gistJson = JSONObject(getResponseText)
            val filesObj = gistJson.optJSONObject("files") ?: JSONObject()

            // 2. Parse mesh_activity.json
            val activityFile = filesObj.optJSONObject("mesh_activity.json")
            val activityContent = activityFile?.optString("content", "") ?: ""
            val activityJson = if (activityContent.isNotBlank()) {
                try { JSONObject(activityContent) } catch (_: Exception) { JSONObject() }
            } else {
                JSONObject()
            }

            val usersObj = activityJson.optJSONObject("users") ?: JSONObject()
            val existingVacationsArray = activityJson.optJSONArray("vacations") ?: JSONArray()
            val allVacations = mutableSetOf<String>()
            for (i in 0 until existingVacationsArray.length()) {
                val d = existingVacationsArray.optString(i)
                if (d.isNotBlank()) allVacations.add(d)
            }
            allVacations.addAll(vacationDates.filter { it.length == 10 })

            // 3. Update current user
            val nowSec = System.currentTimeMillis() / 1000L
            val userKey = installId.take(12)
            usersObj.put(userKey, nowSec)

            // 4. Calculate active users:
            // "если заходил позже 14 не считать, на каникулы таймер 14 дней остановить"
            val today = Calendar.getInstance()
            val activeUsersMap = mutableMapOf<String, Long>()
            val keys = usersObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val ts = usersObj.optLong(k, 0L)
                if (ts > 0 && isUserActive(ts, today, allVacations)) {
                    activeUsersMap[k] = ts
                } else if (ts > 0 && (nowSec - ts) < 45L * 24 * 3600) {
                    // Keep recent inactive entries within 45 days so when vacations end they can resume
                    activeUsersMap[k] = ts
                }
            }

            val activeCount = maxOf(1, activeUsersMap.count { (_, ts) -> isUserActive(ts, today, allVacations) })

            // 5. Build updated files payload
            val updatedUsersObj = JSONObject()
            activeUsersMap.forEach { (k, ts) -> updatedUsersObj.put(k, ts) }

            val updatedVacationsArray = JSONArray()
            allVacations.sorted().forEach { updatedVacationsArray.put(it) }

            val newActivityJson = JSONObject().apply {
                put("last_updated", nowSec)
                put("active_count", activeCount)
                put("users", updatedUsersObj)
                put("vacations", updatedVacationsArray)
            }

            val newStatsJson = JSONObject().apply {
                put("schemaVersion", 1)
                put("label", "активные скачивания")
                put("message", activeCount.toString())
                put("color", "2563EB")
            }

            val patchBody = JSONObject().apply {
                val filesPayload = JSONObject()
                filesPayload.put("mesh_stats.json", JSONObject().apply {
                    put("content", newStatsJson.toString())
                })
                filesPayload.put("mesh_activity.json", JSONObject().apply {
                    put("content", newActivityJson.toString(2))
                })
                put("files", filesPayload)
            }

            // 6. PATCH gist
            val patchUrl = URL("https://api.github.com/gists/$GIST_ID")
            val patchConn = (patchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $GIST_TOKEN")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MeshExpressive-Android")
                setRequestProperty("Content-Type", "application/json")
            }

            OutputStreamWriter(patchConn.outputStream).use { it.write(patchBody.toString()) }
            return@withContext patchConn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun isUserActive(lastSeenSec: Long, today: Calendar, vacationDates: Set<String>): Boolean {
        val lastSeenCal = Calendar.getInstance().apply { timeInMillis = lastSeenSec * 1000L }
        val cur = (lastSeenCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        var nonVacationDays = 0
        while (cur.get(Calendar.YEAR) < today.get(Calendar.YEAR) ||
            (cur.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cur.get(Calendar.DAY_OF_YEAR) <= today.get(Calendar.DAY_OF_YEAR))) {
            val dateStr = sdf.format(cur.time)
            if (!vacationDates.contains(dateStr)) {
                nonVacationDays++
            }
            cur.add(Calendar.DAY_OF_YEAR, 1)
        }
        return nonVacationDays <= 14
    }
}
