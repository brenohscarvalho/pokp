package com.pokp.app.domain

enum class DownloadStatus {
    QUEUED, RESOLVING, DOWNLOADING, SAVING, DONE, FAILED, CANCELLED;

    val isActive: Boolean get() = this == RESOLVING || this == DOWNLOADING || this == SAVING
    val isFinished: Boolean get() = this == DONE || this == FAILED || this == CANCELLED
}

/** Immutable snapshot of one download shown in the UI list. */
data class DownloadTask(
    val request: DownloadRequest,
    val title: String = request.presetTitle,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val etaSeconds: Long = -1,
    val savedUri: String? = null,
    val thumbnail: String? = request.meta?.coverUrl,
    val error: String? = null,
) {
    val id: String get() = request.id
    val kind: LinkKind get() = request.kind
    val format: DownloadFormat get() = request.format
    val sourceUrl: String get() = request.displayUrl
}
