package com.pokp.app.domain

/**
 * The format/quality the user can pick. Each value knows how to translate itself
 * into yt-dlp command-line options (applied in [com.pokp.app.data.YoutubeDlDownloader]).
 */
enum class DownloadFormat(
    val label: String,
    val isAudio: Boolean,
    /** Max video height for the yt-dlp format selector; null for audio or "best". */
    val maxHeight: Int?,
) {
    AUDIO_MP3("MP3 (áudio)", isAudio = true, maxHeight = null),
    VIDEO_360("MP4 360p", isAudio = false, maxHeight = 360),
    VIDEO_480("MP4 480p", isAudio = false, maxHeight = 480),
    VIDEO_720("MP4 720p", isAudio = false, maxHeight = 720),
    VIDEO_1080("MP4 1080p", isAudio = false, maxHeight = 1080),
    VIDEO_BEST("MP4 melhor", isAudio = false, maxHeight = null);

    companion object {
        val videoOptions = listOf(VIDEO_360, VIDEO_480, VIDEO_720, VIDEO_1080, VIDEO_BEST)
        val audioOptions = listOf(AUDIO_MP3)
    }
}
