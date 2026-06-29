package com.pokp.app.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** A single resolved track to be matched & downloaded from YouTube. */
data class SpotifyTrack(
    val title: String,
    val artist: String,
    val durationSec: Int,
) {
    /** Query used for the YouTube Music / YouTube search. */
    fun searchQuery(): String = "$artist - $title"
}

/**
 * Turns a Spotify share URL (track / album / playlist) into the list of tracks it contains,
 * by reading public catalog metadata from the Spotify Web API.
 */
class SpotifyResolver(
    private val client: OkHttpClient,
    private val auth: SpotifyAuth,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = auth.isConfigured

    suspend fun resolve(rawUrl: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val (type, id) = parse(rawUrl) ?: error("Link do Spotify não reconhecido")
        when (type) {
            "track" -> listOf(fetchTrack(id))
            "album" -> fetchAlbumTracks(id)
            "playlist" -> fetchPlaylistTracks(id)
            else -> error("Tipo de link do Spotify não suportado: $type")
        }
    }

    /** Parses both `https://open.spotify.com/<type>/<id>?si=...` and `spotify:<type>:<id>`. */
    internal fun parse(rawUrl: String): Pair<String, String>? {
        val url = rawUrl.trim()
        Regex("spotify:(track|album|playlist):([A-Za-z0-9]+)").find(url)?.let {
            return it.groupValues[1] to it.groupValues[2]
        }
        Regex("open\\.spotify\\.com/(?:intl-[a-z]+/)?(track|album|playlist)/([A-Za-z0-9]+)")
            .find(url)?.let { return it.groupValues[1] to it.groupValues[2] }
        return null
    }

    private suspend fun get(path: String): JsonObject {
        val token = auth.token()
        val request = Request.Builder()
            .url("https://api.spotify.com/v1$path")
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 429) error("Spotify limitou as requisições (429). Tente mais tarde.")
            if (!resp.isSuccessful) error("Erro da API do Spotify (${resp.code})")
            return json.parseToJsonElement(body).jsonObject
        }
    }

    private fun trackFromJson(obj: JsonObject): SpotifyTrack {
        val title = obj["name"]?.jsonPrimitive?.content.orEmpty()
        val artist = obj["artists"]?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
            ?.joinToString(", ").orEmpty()
        val durationMs = obj["duration_ms"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        return SpotifyTrack(title, artist, durationMs / 1000)
    }

    private suspend fun fetchTrack(id: String): SpotifyTrack = trackFromJson(get("/tracks/$id"))

    private suspend fun fetchAlbumTracks(id: String): List<SpotifyTrack> {
        val result = mutableListOf<SpotifyTrack>()
        var path: String? = "/albums/$id/tracks?limit=50"
        while (path != null) {
            val page = get(path)
            page["items"]?.jsonArray?.forEach { result.add(trackFromJson(it.jsonObject)) }
            path = nextPath(page)
        }
        return result
    }

    private suspend fun fetchPlaylistTracks(id: String): List<SpotifyTrack> {
        val result = mutableListOf<SpotifyTrack>()
        var path: String? = "/playlists/$id/tracks?limit=100"
        while (path != null) {
            val page = get(path)
            page["items"]?.jsonArray?.forEach { item ->
                item.jsonObject["track"]?.jsonObject?.let { result.add(trackFromJson(it)) }
            }
            path = nextPath(page)
        }
        return result
    }

    /** Spotify paginated endpoints return an absolute `next` URL (or null) for the next page. */
    private fun nextPath(page: JsonObject): String? {
        val next = page["next"]?.jsonPrimitive?.content
        return if (next.isNullOrBlank() || next == "null") null
        else next.removePrefix("https://api.spotify.com/v1")
    }
}
