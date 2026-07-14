package com.pokp.app.domain

/** MP3 bitrate the user can choose. Maps to yt-dlp `--audio-quality`. */
enum class AudioBitrate(val label: String, val ytdlpValue: String) {
    BEST("Melhor", "0"),
    K320("320 kbps", "320K"),
    K256("256 kbps", "256K"),
    K128("128 kbps", "128K");

    companion object {
        val all = listOf(BEST, K320, K256, K128)
    }
}

/** Track metadata (from Spotify) used to tag the resulting MP3. */
data class TrackMeta(
    val title: String,
    val artist: String,
    val album: String?,
    val coverUrl: String?,
)

/** Light/dark/system theme preference. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Everything needed to perform one download. Created when the user enqueues and carried
 * through the queue/worker unchanged.
 *
 * @param source URL or `ytsearch1:...` search expression yt-dlp downloads from.
 * @param displayUrl the original link shown in the UI.
 * @param subDir optional subfolder (playlist/album name) under Music/Downloads/PokpDownloader.
 * @param meta Spotify metadata used to tag the MP3 (null for plain YouTube downloads).
 */
data class DownloadRequest(
    val id: String,
    val source: String,
    val displayUrl: String,
    val kind: LinkKind,
    val format: DownloadFormat,
    val bitrate: AudioBitrate = AudioBitrate.BEST,
    val subtitles: Boolean = false,
    val trimStart: String? = null,
    val trimEnd: String? = null,
    val subDir: String? = null,
    val presetTitle: String = "",
    val meta: TrackMeta? = null,
)
