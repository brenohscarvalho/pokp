package com.pokp.app.spotify

import android.util.Base64
import com.pokp.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class SpotifyNotConfiguredException :
    Exception("Credenciais do Spotify ausentes. Configure SPOTIFY_CLIENT_ID/SECRET.")

/**
 * Obtains and caches an app-only access token using the Client Credentials flow.
 * Credentials come from BuildConfig (local.properties locally, Actions secrets in CI).
 */
class SpotifyAuth(private val client: OkHttpClient) {

    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var expiresAtMs: Long = 0L

    val isConfigured: Boolean
        get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    suspend fun token(): String = withContext(Dispatchers.IO) {
        if (!isConfigured) throw SpotifyNotConfiguredException()
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < expiresAtMs - 30_000) return@withContext it }

        val basic = Base64.encodeToString(
            "$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP
        )
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .addHeader("Authorization", "Basic $basic")
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Falha na autenticação Spotify (${resp.code}): $body")
            val json = Json.parseToJsonElement(body).jsonObject
            val token = json["access_token"]?.jsonPrimitive?.content
                ?: error("Resposta de token do Spotify inválida")
            val expiresIn = json["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
            cachedToken = token
            expiresAtMs = now + expiresIn * 1000
            token
        }
    }
}
