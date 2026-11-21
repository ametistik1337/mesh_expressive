package ru.mesh.expressive.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.mesh.expressive.data.local.SessionManager
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class OAuthManager(
    private val sessionManager: SessionManager,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val gson = Gson()

    companion object {
        const val REDIRECT_URI = "dnevnik-mes://oauth2redirect"
        const val AUTH_ENDPOINT = "https://login.mos.ru/sps/oauth/ae"
        const val TOKEN_ENDPOINT = "https://login.mos.ru/sps/oauth/te"
        const val REGISTER_ENDPOINT = "https://login.mos.ru/sps/oauth/register"
        const val MESH_AUTH_ENDPOINT = "https://school.mos.ru/v3/auth/sudir/auth"
        const val MESH_REFRESH_ENDPOINT = "https://school.mos.ru/v3/token/refresh"
        const val SCOPES = "birthday+contacts+openid+profile+snils+blitz_change_password+blitz_user_rights+blitz_qr_auth"

        private const val INITIAL_ACCESS_TOKEN =
            "ZPw1bweVJlJAGrRzXRTjPpToF53DOWCr2c5hGSUJQZvuE9MtLEOuQXGSFAAmiT5NlAYyzTwUaWbsaXLg7s0Pvg"

        private const val SOFTWARE_STATEMENT =
            "eyJraWQiOiJkZWZhdWx0IiwidHlwIjoiSldUIiwiYmxpdHo6a2luZCI6IlNPRlRfU1RNIiwiYWxnIjoiUlMyNTYifQ.eyJncmFudF90eXBlcyI6WyJhdXRob3JpemF0aW9uX2NvZGUiLCJjbGllbnRfY3JlZGVudGlhbHMiLCJyZWZyZXNoX3Rva2VuIiwidXJuOmlldGY6cGFyYW1zOm9hdXRoOmdyYW50LXR5cGU6dG9rZW4tZXhjaGFuZ2UiXSwic2NvcGUiOiJiaXJ0aGRheSBibGl0el9jaGFuZ2VfcGFzc3dvcmQgYmxpdHpfcXJfYXV0aCBibGl0el91c2VyX3JpZ2h0cyBjb250YWN0cyBvcGVuaWQgcHJvZmlsZSBzbmlscyIsImp0aSI6ImZmZjkwZWQ1LTZiZGQtNDU3OC1hYTMyLTc3YzQ0NTIwY2YzNSIsInNvZnR3YXJlX2lkIjoiZG5ldm5pay1tZXMiLCJzb2Z0d2FyZV92ZXJzaW9uIjoiMyIsInJlc3BvbnNlX3R5cGVzIjpbImNvZGUiXSwiaWF0IjoxNzg1OTI1NTMwLCJpc3MiOiJodHRwczovL2xvZ2luLm1vcy5ydSIsInJlZGlyZWN0X3VyaXMiOlsiZG5ldm5pay1tZXM6LyJdLCJhdWQiOlsiZG5ldm5pay1tZXMiXX0.MdwxLwmvFsGHAYYrq7EVR-uiMy7w3-cVc4UXCi2dU1uQjSFucgvVju4hl_EXlFHKbj6WHn_RRIM9RTku2h7FXhr0SmEv1Dm47oDb3CPhtcipSjPdQ0xKLU1dymM_HTsAYHGtlwIFdUmsPnFE2BOHjQ6HOr6lzLJ0mk7WxRkZDqkYehxhhT2ZV3b9SplggJBP92UId6X1Ad2QpRLATiPEKqViRbd1coEpdD907jYOE0lMwJ8f2S5C2mAsUKdTZrqHR7rnPvm5LTlIN0yBd3d92jkUYmgHBSMwki8YQb7gb736iZv1XZf6eN2CQP1jNDsuQeggUaMx_jws1D7t1qRSJg"
    }

    private data class RegisterRequest(
        @SerializedName("software_id") val softwareId: String,
        @SerializedName("device_type") val deviceType: String,
        @SerializedName("software_statement") val softwareStatement: String
    )

    private data class RegisterResponse(
        @SerializedName("client_id") val clientId: String,
        @SerializedName("client_secret") val clientSecret: String,
        @SerializedName("registration_client_uri") val registrationClientUri: String?,
        @SerializedName("registration_access_token") val registrationAccessToken: String?
    )

    private data class MosTokenResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("expires_in") val expiresIn: Long?
    )

    private data class MeshAuthRequest(
        @SerializedName("user_authentication_for_mobile_request") val payload: MeshAuthPayload
    )

    private data class MeshAuthPayload(
        @SerializedName("mos_access_token") val mosAccessToken: String
    )

    private data class MeshAuthResponse(
        @SerializedName("token") val token: String,
        @SerializedName("refreshToken") val refreshToken: String?,
        @SerializedName("profile_id") val profileId: String?
    )

    private data class MeshRefreshResponse(
        @SerializedName("token") val token: String?,
        @SerializedName("refreshToken") val refreshToken: String?
    )

    suspend fun getOrRegisterClient(): Pair<String, String> = withContext(Dispatchers.IO) {
        val existingClientId = sessionManager.oauthClientId
        val existingClientSecret = sessionManager.oauthClientSecret
        if (!existingClientId.isNullOrBlank() && !existingClientSecret.isNullOrBlank()) {
            return@withContext Pair(existingClientId, existingClientSecret)
        }

        val regBody = RegisterRequest(
            softwareId = "dnevnik-mes",
            deviceType = "mobile",
            softwareStatement = SOFTWARE_STATEMENT
        )
        val jsonBody = gson.toJson(regBody).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(REGISTER_ENDPOINT)
            .post(jsonBody)
            .addHeader("Authorization", "Bearer $INITIAL_ACCESS_TOKEN")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorText = response.body?.string() ?: ""
            throw IllegalStateException("Ошибка регистрации клиента OAuth: HTTP ${response.code} $errorText")
        }

        val respBody = response.body?.string() ?: throw IllegalStateException("Пустой ответ от сервера регистрации")
        val parsed = gson.fromJson(respBody, RegisterResponse::class.java)
        sessionManager.oauthClientId = parsed.clientId
        sessionManager.oauthClientSecret = parsed.clientSecret

        Pair(parsed.clientId, parsed.clientSecret)
    }

    suspend fun startAuthFlow(context: Context): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            val (clientId, _) = withContext(Dispatchers.IO) { getOrRegisterClient() }

            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)
            val state = UUID.randomUUID().toString()

            sessionManager.oauthCodeVerifier = codeVerifier
            sessionManager.oauthState = state

            val authUrl = Uri.parse(AUTH_ENDPOINT).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "birthday contacts openid profile snils blitz_change_password blitz_user_rights blitz_qr_auth")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build()

            try {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.launchUrl(context, authUrl)
            } catch (_: Exception) {
                // Fallback to standard browser Intent
                val browserIntent = Intent(Intent.ACTION_VIEW, authUrl).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun handleAuthRedirect(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val code = uri.getQueryParameter("code")
                ?: return@withContext Result.failure(IllegalArgumentException("Параметр 'code' отсутствует в ссылке возврата"))
            val state = uri.getQueryParameter("state")
            val savedState = sessionManager.oauthState

            if (savedState != null && state != null && savedState != state) {
                return@withContext Result.failure(IllegalStateException("Несовпадение параметра state (CSRF защита)"))
            }

            val codeVerifier = sessionManager.oauthCodeVerifier
                ?: return@withContext Result.failure(IllegalStateException("Параметр code_verifier отсутствует в сессии"))

            val (clientId, clientSecret) = getOrRegisterClient()

            // 1. Обмен кода авторизации на токен mos.ru
            val basicCredentials = Base64.encodeToString("$clientId:$clientSecret".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val tokenRequestBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()

            val tokenRequest = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(tokenRequestBody)
                .addHeader("Authorization", "Basic $basicCredentials")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val tokenResponse = okHttpClient.newCall(tokenRequest).execute()
            if (!tokenResponse.isSuccessful) {
                val errBody = tokenResponse.body?.string() ?: ""
                return@withContext Result.failure(IllegalStateException("Ошибка обмена кода mos.ru: HTTP ${tokenResponse.code} $errBody"))
            }

            val mosTokenJson = tokenResponse.body?.string() ?: ""
            val mosTokens = gson.fromJson(mosTokenJson, MosTokenResponse::class.java)
            sessionManager.mosAccessToken = mosTokens.accessToken
            sessionManager.mosRefreshToken = mosTokens.refreshToken

            // 2. Обмен токена mos.ru на JWT токен МЭШ (АУПД)
            val meshRequestBody = MeshAuthRequest(
                payload = MeshAuthPayload(mosAccessToken = mosTokens.accessToken)
            )
            val meshJsonBody = gson.toJson(meshRequestBody).toRequestBody("application/json".toMediaType())
            val meshRequest = Request.Builder()
                .url(MESH_AUTH_ENDPOINT)
                .post(meshJsonBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val meshResponse = okHttpClient.newCall(meshRequest).execute()
            if (!meshResponse.isSuccessful) {
                val errBody = meshResponse.body?.string() ?: ""
                return@withContext Result.failure(IllegalStateException("Ошибка авторизации в МЭШ: HTTP ${meshResponse.code} $errBody"))
            }

            val meshRespJson = meshResponse.body?.string() ?: ""
            val meshAuth = gson.fromJson(meshRespJson, MeshAuthResponse::class.java)

            sessionManager.authToken = meshAuth.token
            sessionManager.meshRefreshToken = meshAuth.refreshToken
            if (!meshAuth.profileId.isNullOrBlank()) {
                sessionManager.profileId = meshAuth.profileId
            }

            Result.success(meshAuth.token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshTokens(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Попытка 1: Рефреш токена МЭШ напрямую по refresh_token
            val meshRefresh = sessionManager.meshRefreshToken
            if (!meshRefresh.isNullOrBlank()) {
                val formBody = FormBody.Builder()
                    .add("refresh_token", meshRefresh)
                    .build()
                val request = Request.Builder()
                    .url(MESH_REFRESH_ENDPOINT)
                    .post(formBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val parsed = gson.fromJson(body, MeshRefreshResponse::class.java)
                    if (!parsed.token.isNullOrBlank()) {
                        sessionManager.authToken = parsed.token
                        if (!parsed.refreshToken.isNullOrBlank()) {
                            sessionManager.meshRefreshToken = parsed.refreshToken
                        }
                        return@withContext Result.success(parsed.token)
                    }
                }
            }

            // Попытка 2: Рефреш токена mos.ru через client_credentials/refresh_token
            val mosRefresh = sessionManager.mosRefreshToken
            if (!mosRefresh.isNullOrBlank()) {
                val (clientId, clientSecret) = getOrRegisterClient()
                val basicCredentials = Base64.encodeToString("$clientId:$clientSecret".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val tokenRequestBody = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", mosRefresh)
                    .build()

                val tokenRequest = Request.Builder()
                    .url(TOKEN_ENDPOINT)
                    .post(tokenRequestBody)
                    .addHeader("Authorization", "Basic $basicCredentials")
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                val tokenResponse = okHttpClient.newCall(tokenRequest).execute()
                if (tokenResponse.isSuccessful) {
                    val mosTokenJson = tokenResponse.body?.string() ?: ""
                    val mosTokens = gson.fromJson(mosTokenJson, MosTokenResponse::class.java)
                    sessionManager.mosAccessToken = mosTokens.accessToken
                    if (!mosTokens.refreshToken.isNullOrBlank()) {
                        sessionManager.mosRefreshToken = mosTokens.refreshToken
                    }

                    // Обмен обновленного токена mos.ru на токен МЭШ
                    val meshRequestBody = MeshAuthRequest(
                        payload = MeshAuthPayload(mosAccessToken = mosTokens.accessToken)
                    )
                    val meshJsonBody = gson.toJson(meshRequestBody).toRequestBody("application/json".toMediaType())
                    val meshRequest = Request.Builder()
                        .url(MESH_AUTH_ENDPOINT)
                        .post(meshJsonBody)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    val meshResponse = okHttpClient.newCall(meshRequest).execute()
                    if (meshResponse.isSuccessful) {
                        val meshRespJson = meshResponse.body?.string() ?: ""
                        val meshAuth = gson.fromJson(meshRespJson, MeshAuthResponse::class.java)
                        sessionManager.authToken = meshAuth.token
                        if (!meshAuth.refreshToken.isNullOrBlank()) {
                            sessionManager.meshRefreshToken = meshAuth.refreshToken
                        }
                        return@withContext Result.success(meshAuth.token)
                    }
                }
            }

            Result.failure(IllegalStateException("Не удалось обновить токен в фоне"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
