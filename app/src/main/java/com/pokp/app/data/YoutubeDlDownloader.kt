package com.pokp.app.data

import com.pokp.app.domain.DownloadFormat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadResult(val file: File, val isAudio: Boolean, val mimeType: String)

/**
 * Thin wrapper around yt-dlp (via youtubedl-android). Each download runs in its own
 * temporary directory so we can reliably pick up the resulting file afterwards.
 *
 * [source] may be a normal URL or a search expression such as `ytsearch1:Artist - Title`
 * (used by the Spotify bridge).
 */
object YoutubeDlDownloader {

    suspend fun fetchTitle(source: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val info = YoutubeDL.getInstance().getInfo(YoutubeDLRequest(source))
            info.title ?: info.fulltitle
        }.getOrNull()
    }

    /**
     * Downloads [source] in the chosen [format] and returns the produced file.
     * @param onProgress invoked with (progress 0..100, etaSeconds, rawLine).
     */
    suspend fun download(
        source: String,
        format: DownloadFormat,
        processId: String,
        baseCacheDir: File,
        onProgress: (Float, Long, String) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val workDir = File(baseCacheDir, "dl_$processId").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val request = YoutubeDLRequest(source).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--restrict-filenames")
                addOption("-o", "${workDir.absolutePath}/%(title)s.%(ext)s")
                applyFormatOptions(format)
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                onProgress(progress, eta, line)
            }

            val produced = pickResultFile(workDir, format)
                ?: error("Download concluído mas nenhum arquivo foi encontrado")

            // Move out of the per-task work dir into a flat cache so the dir can be cleaned.
            val finalFile = File(baseCacheDir, produced.name)
            if (finalFile.exists()) finalFile.delete()
            produced.copyTo(finalFile, overwrite = true)

            DownloadResult(
                file = finalFile,
                isAudio = format.isAudio,
                mimeType = if (format.isAudio) "audio/mpeg" else "video/mp4",
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    fun cancel(processId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    private fun YoutubeDLRequest.applyFormatOptions(format: DownloadFormat) {
        if (format.isAudio) {
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "0")
            addOption("--embed-metadata")
            addOption("--embed-thumbnail")
        } else {
            val selector = when (val h = format.maxHeight) {
                null -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                else -> "bestvideo[height<=$h][ext=mp4]+bestaudio[ext=m4a]/best[height<=$h]"
            }
            addOption("-f", selector)
            addOption("--merge-output-format", "mp4")
        }
    }

    private fun pickResultFile(dir: File, format: DownloadFormat): File? {
        val files = dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?: return null
        val wantedExt = if (format.isAudio) "mp3" else "mp4"
        return files.firstOrNull { it.extension.equals(wantedExt, ignoreCase = true) }
            ?: files.maxByOrNull { it.length() }
    }
}
