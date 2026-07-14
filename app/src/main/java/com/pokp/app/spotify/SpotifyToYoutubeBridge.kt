package com.pokp.app.spotify

/**
 * Bridges Spotify metadata to a YouTube search expression that yt-dlp can download.
 *
 * Spotify audio is DRM-protected and cannot be downloaded directly, so we look the track
 * up on YouTube and download that audio instead — the same approach used by spotDL /
 * SpotiFlyer.
 */
object SpotifyToYoutubeBridge {

    /**
     * Builds the yt-dlp source string. `ytsearch1:` is yt-dlp's built-in YouTube search key
     * (there is no `ytmsearch` scheme); it resolves to the single best matching result.
     */
    fun searchSource(track: SpotifyTrack): String =
        "ytsearch1:${track.searchQuery()}"
}
