package com.pokp.app.data

import com.pokp.app.domain.DownloadFormat
import com.pokp.app.domain.DownloadRequest
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadResult(val file: File, val isAudio: Boolean, val mimeType: String)

/** Basic info used for the pre-download preview. */
data class MediaInfo(val title: String, val thumbnail: String?, val durationSec: Int)

/** One entry of a YouTube playlist (used when expanding a playlist into the queue). */
data class PlaylistEntry(val url: String, val title: String)

/**
 * Thin wrapper around yt-dlp (via youtubedl-android). Each download runs in its own
 * temporary directory so we can reliably pick up the resulting file afterwards.
 */
object YoutubeDlDownloader {

    suspend fun fetchTitle(source: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val info = YoutubeDL.getInstance().getInfo(YoutubeDLRequest(source))
            info.title ?: info.fulltitle
        }.getOrNull()
    }

    suspend fun fetchInfo(source: String): MediaInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val info = YoutubeDL.getInstance().getInfo(YoutubeDLRequest(source))
            MediaInfo(
                title = info.title ?: info.fulltitle ?: source,
                thumbnail = info.thumbnail,
                durationSec = info.duration,
            )
        }.getOrNull()
    }

    /** Lists the entries of a YouTube playlist without downloading (flat, fast). */
    suspend fun listPlaylist(url: String): List<PlaylistEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val request = YoutubeDLRequest(url).apply {
                addOption("--flat-playlist")
                addOption("--print", "%(id)s\t%(title)s")
            }
            val response = YoutubeDL.getInstance().execute(request)
            response.out.lineSequence()
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    val id = parts.getOrNull(0)?.trim().orEmpty()
                    if (id.isBlank()) return@mapNotNull null
                    PlaylistEntry(
                        url = "https://www.youtube.com/watch?v=$id",
                        title = parts.getOrNull(1)?.trim().orEmpty(),
                    )
                }
                .toList()
        }.getOrDefault(emptyList())
    }

    /**
     * Downloads [request] and returns the produced file.
     * @param onProgress invoked with (progress 0..100, etaSeconds, rawLine).
     */
    suspend fun download(
        request: DownloadRequest,
        baseCacheDir: File,
        compatMode: Boolean = false,
        onProgress: (Float, Long, String) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val processId = request.id.replace("-", "")
        val workDir = File(baseCacheDir, "dl_$processId").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val ytdl = YoutubeDLRequest(request.source).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--restrict-filenames")
                addOption("-o", "${workDir.absolutePath}/%(title)s.%(ext)s")
                if (compatMode) {
                    // The android player client skips YouTube's JS signature challenges —
                    // fewer formats, but downloads keep working when the solver is stale.
                    addOption("--extractor-args", "youtube:player_client=android,web_safari")
                }
                applyOptions(request)
            }

            YoutubeDL.getInstance().execute(ytdl, processId) { progress, eta, line ->
                onProgress(progress, eta, line)
            }

            val produced = pickResultFile(workDir, request.format)
                ?: error("Download concluído mas nenhum arquivo foi encontrado")

            val finalFile = File(baseCacheDir, produced.name)
            if (finalFile.exists()) finalFile.delete()
            produced.copyTo(finalFile, overwrite = true)

            DownloadResult(
                file = finalFile,
                isAudio = request.format.isAudio,
                mimeType = if (request.format.isAudio) "audio/mpeg" else "video/mp4",
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    fun cancel(processId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    private fun YoutubeDLRequest.applyOptions(request: DownloadRequest) {
        val format = request.format
        if (format.isAudio) {
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", request.bitrate.ytdlpValue)
            // For plain YouTube audio, let yt-dlp embed tags/thumbnail; Spotify tracks are
            // tagged afterwards with accurate metadata, so skip embedding here.
            if (request.meta == null) {
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
            }
        } else {
            val selector = when (val h = format.maxHeight) {
                null -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                else -> "bestvideo[height<=$h][ext=mp4]+bestaudio[ext=m4a]/best[height<=$h]"
            }
            addOption("-f", selector)
            addOption("--merge-output-format", "mp4")
            if (request.subtitles) {
                addOption("--write-subs")
                addOption("--write-auto-subs")
                addOption("--sub-langs", "pt.*,en.*")
                addOption("--convert-subs", "srt")
                addOption("--embed-subs")
            }
        }

        val trim = trimSection(request.trimStart, request.trimEnd)
        if (trim != null) {
            addOption("--download-sections", trim)
            addOption("--force-keyframes-at-cuts")
        }
    }

    /** Builds a yt-dlp `--download-sections` value like `*00:10-01:30`; null if no trim set. */
    private fun trimSection(start: String?, end: String?): String? {
        val s = start?.trim().orEmpty()
        val e = end?.trim().orEmpty()
        if (s.isBlank() && e.isBlank()) return null
        return "*${s.ifBlank { "0" }}-${e.ifBlank { "inf" }}"
    }

    private fun pickResultFile(dir: File, format: DownloadFormat): File? {
        val files = dir.listFiles()
            ?.filter {
                it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") &&
                    !it.name.endsWith(".srt")
            }
            ?: return null
        val wantedExt = if (format.isAudio) "mp3" else "mp4"
        return files.firstOrNull { it.extension.equals(wantedExt, ignoreCase = true) }
            ?: files.maxByOrNull { it.length() }
    }
}
