package com.pokp.app.spotify

/**
 * Bridges Spotify metadata to a YouTube search expression that yt-dlp can download.
 *
 * Spotify audio is DRM-protected and cannot be downloaded directly, so we look the track
 * up on YouTube Music (falling back to YouTube) and download that audio instead — the same
 * approach used by spotDL / SpotiFlyer.
 */
object SpotifyToYoutubeBridge {

    /**
     * Builds the yt-dlp source string. `ytmsearch1:` queries YouTube Music (better audio
     * matches); yt-dlp resolves it to the single best result.
     */
    fun searchSource(track: SpotifyTrack): String =
        "ytmsearch1:${track.searchQuery()}"
}
