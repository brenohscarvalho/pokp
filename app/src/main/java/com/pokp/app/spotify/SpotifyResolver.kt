package com.pokp.app.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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
    /** Query used for the YouTube search. */
    fun searchQuery(): String = if (artist.isBlank()) title else "$artist - $title"
}

/**
 * Result of resolving a Spotify link.
 * @param collectionName playlist/album name (null for a single track) — used as a subfolder.
 */
data class SpotifyResolution(
    val collectionName: String?,
    val tracks: List<SpotifyTrack>,
)

/**
 * Turns a Spotify share URL (track / album / playlist) into the list of tracks it contains.
 *
 * Primary path is the official Web API (Client Credentials). Spotify blocks its own editorial
 * / algorithmic playlists for that flow (HTTP 403), so we fall back to the public embed page
 * (`open.spotify.com/embed/...`), which requires no credentials — the same trick spotDL /
 * SpotiFlyer use.
 */
class SpotifyResolver(
    private val client: OkHttpClient,
    private val auth: SpotifyAuth,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = auth.isConfigured

    suspend fun resolve(rawUrl: String): SpotifyResolution = withContext(Dispatchers.IO) {
        val (type, id) = parse(rawUrl) ?: error("Link do Spotify não reconhecido")
        // Try the official API first; on any failure (e.g. 403 for editorial playlists, no
        // credentials, rate limit) fall back to the public embed page.
        val viaApi = runCatching { resolveViaApi(type, id) }.getOrNull()
        if (viaApi != null && viaApi.tracks.isNotEmpty()) viaApi else resolveViaEmbed(type, id)
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

    // ---- Official Web API (Client Credentials) ----------------------------------------------

    private suspend fun resolveViaApi(type: String, id: String): SpotifyResolution? {
        if (!auth.isConfigured) return null
        return when (type) {
            "track" -> SpotifyResolution(null, listOf(fetchTrack(id)))
            "album" -> SpotifyResolution(fetchName("/albums/$id"), fetchAlbumTracks(id))
            "playlist" ->
                SpotifyResolution(fetchName("/playlists/$id?fields=name"), fetchPlaylistTracks(id))
            else -> null
        }
    }

    private suspend fun fetchName(path: String): String? =
        runCatching { get(path)["name"]?.jsonPrimitive?.contentOrNull }.getOrNull()

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
        val title = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val artist = obj["artists"]?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString(", ").orEmpty()
        val durationMs = obj["duration_ms"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
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
        val next = page["next"]?.jsonPrimitive?.contentOrNull
        return if (next.isNullOrBlank() || next == "null") null
        else next.removePrefix("https://api.spotify.com/v1")
    }

    // ---- Public embed fallback (no credentials, works for editorial playlists) --------------

    private fun resolveViaEmbed(type: String, id: String): SpotifyResolution {
        val request = Request.Builder()
            .url("https://open.spotify.com/embed/$type/$id")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/122.0 Safari/537.36",
            )
            .header("Accept-Language", "en")
            .build()

        val html = client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("Não foi possível ler o link do Spotify (${resp.code})")
            }
            body
        }

        val nextData = extractNextData(html)
            ?: error("Não consegui interpretar a página do Spotify")
        val root = json.parseToJsonElement(nextData)

        val entity = findEntityWithTrackList(root)
        if (entity != null) {
            val name = entity["name"]?.jsonPrimitive?.contentOrNull
                ?: entity["title"]?.jsonPrimitive?.contentOrNull
            val tracks = (entity["trackList"] as? JsonArray)
                ?.mapNotNull { embedTrack(it) }
                ?.filter { it.title.isNotBlank() }
                .orEmpty()
            if (tracks.isNotEmpty()) {
                // A single-track embed also has a 1-item trackList; treat that as no collection.
                val collection = if (type == "track" || tracks.size <= 1) null else name
                return SpotifyResolution(collection, tracks)
            }
        }

        findTitleSubtitle(root)?.let { return SpotifyResolution(null, listOf(it)) }
        error("Nenhuma faixa encontrada nesse link do Spotify")
    }

    /** Extracts the JSON inside `<script id="__NEXT_DATA__" ...>...</script>`. */
    private fun extractNextData(html: String): String? {
        val idAt = html.indexOf("id=\"__NEXT_DATA__\"")
        if (idAt < 0) return null
        val open = html.indexOf('>', idAt)
        if (open < 0) return null
        val close = html.indexOf("</script>", open)
        if (close < 0) return null
        return html.substring(open + 1, close).trim()
    }

    /** Depth-first search for the first object that directly holds a `trackList` array. */
    private fun findEntityWithTrackList(el: JsonElement): JsonObject? {
        when (el) {
            is JsonObject -> {
                if (el["trackList"] is JsonArray) return el
                for ((_, v) in el) findEntityWithTrackList(v)?.let { return it }
            }
            is JsonArray -> for (v in el) findEntityWithTrackList(v)?.let { return it }
            else -> {}
        }
        return null
    }

    private fun embedTrack(el: JsonElement): SpotifyTrack? {
        val obj = el as? JsonObject ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val durMs = obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        return SpotifyTrack(title, subtitle, (durMs / 1000).toInt())
    }

    /** Fallback for single-item embeds: first object carrying both title and subtitle. */
    private fun findTitleSubtitle(el: JsonElement): SpotifyTrack? {
        when (el) {
            is JsonObject -> {
                if (el["title"] != null && el["subtitle"] != null) {
                    embedTrack(el)?.let { return it }
                }
                for ((_, v) in el) findTitleSubtitle(v)?.let { return it }
            }
            is JsonArray -> for (v in el) findTitleSubtitle(v)?.let { return it }
            else -> {}
        }
        return null
    }
}
