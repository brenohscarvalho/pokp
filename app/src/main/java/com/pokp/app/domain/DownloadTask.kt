package com.pokp.app.domain

enum class DownloadStatus { QUEUED, RESOLVING, DOWNLOADING, SAVING, DONE, FAILED, CANCELLED }

/** Immutable snapshot of one download shown in the UI list. */
data class DownloadTask(
    val id: String,
    val sourceUrl: String,
    val kind: LinkKind,
    val format: DownloadFormat,
    val title: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val etaSeconds: Long = -1,
    val savedUri: String? = null,
    val error: String? = null,
)
