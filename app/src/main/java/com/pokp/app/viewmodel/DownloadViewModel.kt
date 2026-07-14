package com.pokp.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pokp.app.data.InitManager
import com.pokp.app.data.MediaStoreSaver
import com.pokp.app.data.YoutubeDlDownloader
import com.pokp.app.domain.DownloadFormat
import com.pokp.app.domain.DownloadStatus
import com.pokp.app.domain.DownloadTask
import com.pokp.app.domain.LinkKind
import com.pokp.app.domain.UrlClassifier
import com.pokp.app.spotify.SpotifyAuth
import com.pokp.app.spotify.SpotifyResolver
import com.pokp.app.spotify.SpotifyToYoutubeBridge
import com.pokp.app.data.DownloadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    val initState: StateFlow<com.pokp.app.data.InitState> = InitManager.state

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val spotifyAuth by lazy { SpotifyAuth(httpClient) }
    private val spotifyResolver by lazy { SpotifyResolver(httpClient, spotifyAuth) }

    val spotifyConfigured: Boolean get() = spotifyAuth.isConfigured

    fun consumeMessage() { _message.value = null }

    /** Entry point from the UI. [format] is ignored for Spotify (always MP3). */
    fun enqueue(rawInput: String, format: DownloadFormat) {
        val url = UrlClassifier.extractUrl(rawInput) ?: rawInput.trim()
        if (url.isBlank()) {
            _message.value = "Cole um link válido."
            return
        }
        when (UrlClassifier.classify(url)) {
            LinkKind.YOUTUBE -> startSingle(url, LinkKind.YOUTUBE, format, url)
            LinkKind.SPOTIFY -> startSpotify(url)
            LinkKind.UNKNOWN -> _message.value = "Link não reconhecido (use YouTube ou Spotify)."
        }
    }

    private fun startSpotify(url: String) {
        if (!spotifyAuth.isConfigured) {
            _message.value = "Spotify não configurado: adicione SPOTIFY_CLIENT_ID/SECRET."
            return
        }
        viewModelScope.launch {
            InitManager.ensureInitialized(getApplication())
            try {
                val resolution = spotifyResolver.resolve(url)
                val tracks = resolution.tracks
                if (tracks.isEmpty()) {
                    _message.value = "Nenhuma faixa encontrada nesse link do Spotify."
                    return@launch
                }
                // Playlist/album tracks go into a subfolder named after the collection.
                val subDir = resolution.collectionName
                // Sequential to be gentle with Spotify/YouTube rate limits.
                tracks.forEach { track ->
                    val source = SpotifyToYoutubeBridge.searchSource(track)
                    runDownload(
                        source = source,
                        kind = LinkKind.SPOTIFY,
                        format = DownloadFormat.AUDIO_MP3,
                        displayTitle = track.searchQuery(),
                        displaySourceUrl = url,
                        subDir = subDir,
                    )
                }
            } catch (t: Throwable) {
                _message.value = t.message ?: "Falha ao resolver o link do Spotify."
            }
        }
    }

    private fun startSingle(
        source: String,
        kind: LinkKind,
        format: DownloadFormat,
        displaySourceUrl: String,
    ) {
        viewModelScope.launch {
            InitManager.ensureInitialized(getApplication())
            runDownload(source, kind, format, displayTitle = "", displaySourceUrl = displaySourceUrl)
        }
    }

    private suspend fun runDownload(
        source: String,
        kind: LinkKind,
        format: DownloadFormat,
        displayTitle: String,
        displaySourceUrl: String,
        subDir: String? = null,
    ) {
        val id = UUID.randomUUID().toString()
        val processId = id.replace("-", "")
        addTask(
            DownloadTask(
                id = id,
                sourceUrl = displaySourceUrl,
                kind = kind,
                format = format,
                title = displayTitle,
                status = DownloadStatus.RESOLVING,
            )
        )

        // Best-effort title fetch for nicer display (skip if we already have one).
        if (displayTitle.isBlank()) {
            YoutubeDlDownloader.fetchTitle(source)?.let { t ->
                updateTask(id) { it.copy(title = t) }
            }
        }

        try {
            updateTask(id) { it.copy(status = DownloadStatus.DOWNLOADING) }
            val result = downloadWithRetry(id, source, format, processId)

            updateTask(id) { it.copy(status = DownloadStatus.SAVING, progress = 100f) }
            val uri = MediaStoreSaver.save(
                context = getApplication(),
                file = result.file,
                mimeType = result.mimeType,
                isAudio = result.isAudio,
                subDir = subDir,
            )
            result.file.delete()
            updateTask(id) {
                it.copy(status = DownloadStatus.DONE, savedUri = uri.toString())
            }
        } catch (t: Throwable) {
            val cancelled = t is InterruptedException
            updateTask(id) {
                it.copy(
                    status = if (cancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED,
                    error = if (cancelled) null else (t.message ?: "Falha no download"),
                )
            }
        }
    }

    /**
     * Runs the download, retrying a few times on transient network errors (e.g. DNS failures
     * from a momentary connection drop) with a short backoff before giving up.
     */
    private suspend fun downloadWithRetry(
        id: String,
        source: String,
        format: DownloadFormat,
        processId: String,
    ): DownloadResult {
        val maxAttempts = 3
        var attempt = 0
        while (true) {
            try {
                return YoutubeDlDownloader.download(
                    source = source,
                    format = format,
                    processId = processId,
                    baseCacheDir = getApplication<Application>().cacheDir,
                ) { progress, eta, _ ->
                    updateTask(id) { it.copy(progress = progress, etaSeconds = eta) }
                }
            } catch (t: Throwable) {
                attempt++
                if (t is InterruptedException || attempt >= maxAttempts || !isTransientNetworkError(t)) {
                    throw t
                }
                updateTask(id) {
                    it.copy(progress = 0f, error = "Rede instável, tentando novamente ($attempt)…")
                }
                delay(3000L * attempt)
                updateTask(id) { it.copy(error = null) }
            }
        }
    }

    private fun isTransientNetworkError(t: Throwable): Boolean {
        val m = (t.message ?: "").lowercase()
        return listOf(
            "hostname", "errno 7", "temporary failure", "transporterror",
            "unable to download", "timed out", "timeout", "connection reset",
            "network is unreachable", "no address",
        ).any { it in m }
    }

    fun cancel(task: DownloadTask) {
        viewModelScope.launch(Dispatchers.IO) {
            YoutubeDlDownloader.cancel(task.id.replace("-", ""))
        }
        updateTask(task.id) { it.copy(status = DownloadStatus.CANCELLED) }
    }

    fun remove(task: DownloadTask) {
        _tasks.value = _tasks.value.filterNot { it.id == task.id }
    }

    private fun addTask(task: DownloadTask) {
        _tasks.value = listOf(task) + _tasks.value
    }

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }
}
