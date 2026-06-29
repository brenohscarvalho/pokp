package com.pokp.app.domain

enum class LinkKind { YOUTUBE, SPOTIFY, UNKNOWN }

/** Lightweight detection of what kind of link the user pasted. */
object UrlClassifier {

    private val youtubeHosts = listOf(
        "youtube.com", "youtu.be", "music.youtube.com", "m.youtube.com",
    )

    fun classify(rawUrl: String): LinkKind {
        val url = rawUrl.trim().lowercase()
        return when {
            youtubeHosts.any { url.contains(it) } -> LinkKind.YOUTUBE
            url.contains("open.spotify.com") || url.startsWith("spotify:") -> LinkKind.SPOTIFY
            else -> LinkKind.UNKNOWN
        }
    }

    /** Extracts the first http(s) URL or spotify: URI found in a shared text blob. */
    fun extractUrl(text: String): String? {
        val regex = Regex("(https?://\\S+|spotify:\\S+)")
        return regex.find(text.trim())?.value
    }
}
