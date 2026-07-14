package com.pokp.app.data

import android.util.Log
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import com.pokp.app.domain.TrackMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Writes accurate ID3 tags (title/artist/album + cover) from Spotify metadata onto an MP3. */
object Mp3Tagger {

    private const val TAG = "Mp3Tagger"

    suspend fun tag(file: File, meta: TrackMeta, client: OkHttpClient) = withContext(Dispatchers.IO) {
        runCatching {
            val mp3 = Mp3File(file.absolutePath)
            val id3 = if (mp3.hasId3v2Tag()) mp3.id3v2Tag else ID3v24Tag().also { mp3.id3v2Tag = it }
            id3.title = meta.title
            id3.artist = meta.artist
            meta.album?.let { id3.album = it }

            downloadCover(meta.coverUrl, client)?.let { bytes ->
                id3.setAlbumImage(bytes, "image/jpeg")
            }

            val tmp = File(file.parentFile, file.name + ".tagged.mp3")
            mp3.save(tmp.absolutePath)
            if (tmp.exists()) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure { Log.w(TAG, "Falha ao gravar tags: ${it.message}") }
        Unit
    }

    private fun downloadCover(url: String?, client: OkHttpClient): ByteArray? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        }.getOrNull()
    }
}
