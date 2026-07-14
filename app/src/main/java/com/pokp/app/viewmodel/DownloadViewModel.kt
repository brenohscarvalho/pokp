package com.pokp.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pokp.app.BuildConfig
import com.pokp.app.data.DownloadRepository
import com.pokp.app.data.HistoryStore
import com.pokp.app.data.InitManager
import com.pokp.app.data.InitState
import com.pokp.app.data.SettingsStore
import com.pokp.app.data.UpdateChecker
import com.pokp.app.data.YoutubeDlDownloader
import com.pokp.app.domain.AudioBitrate
import com.pokp.app.domain.DownloadFormat
import com.pokp.app.domain.DownloadRequest
import com.pokp.app.domain.DownloadTask
import com.pokp.app.domain.LinkKind
import com.pokp.app.domain.ThemeMode
import com.pokp.app.domain.TrackMeta
import com.pokp.app.domain.UrlClassifier
import com.pokp.app.spotify.SpotifyAuth
import com.pokp.app.spotify.SpotifyResolver
import com.pokp.app.spotify.SpotifyToYoutubeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Options captured from the UI when the user hits download. */
data class EnqueueOptions(
    val format: DownloadFormat,
    val bitrate: AudioBitrate,
    val subtitles: Boolean,
    val trimStart: String?,
    val trimEnd: String?,
    val wholePlaylist: Boolean,
)

/** Info shown in the pre-download preview dialog. */
data class PreviewInfo(
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    val initState: StateFlow<InitState> = InitManager.state
    val tasks: StateFlow<List<DownloadTask>> = DownloadRepository.tasks
    val history = HistoryStore.entries

    val themeMode: StateFlow<ThemeMode> = SettingsStore.themeMode
    val dynamicColor: StateFlow<Boolean> = SettingsStore.dynamicColor
    val bitrate: StateFlow<AudioBitrate> = SettingsStore.bitrate
    val subtitles: StateFlow<Boolean> = SettingsStore.subtitles

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _preview = MutableStateFlow<PreviewInfo?>(null)
    val preview: StateFlow<PreviewInfo?> = _preview.asStateFlow()

    private val _previewLoading = MutableStateFlow(false)
    val previewLoading: StateFlow<Boolean> = _previewLoading.asStateFlow()

    private val _update = MutableStateFlow<UpdateChecker.UpdateInfo?>(null)
    val update: StateFlow<UpdateChecker.UpdateInfo?> = _update.asStateFlow()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val spotifyAuth by lazy { SpotifyAuth(httpClient) }
    private val spotifyResolver by lazy { SpotifyResolver(httpClient, spotifyAuth) }

    val spotifyConfigured: Boolean get() = spotifyAuth.isConfigured

    init {
        checkForUpdate()
    }

    fun consumeMessage() { _message.value = null }
    fun dismissPreview() { _preview.value = null }
    fun dismissUpdate() { _update.value = null }

    fun setThemeMode(mode: ThemeMode) = SettingsStore.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = SettingsStore.setDynamicColor(enabled)
    fun setDefaultBitrate(b: AudioBitrate) = SettingsStore.setBitrate(b)
    fun setDefaultSubtitles(enabled: Boolean) = SettingsStore.setSubtitles(enabled)

    fun isYoutubePlaylist(rawInput: String): Boolean {
        val url = (UrlClassifier.extractUrl(rawInput) ?: rawInput).lowercase()
        return UrlClassifier.classify(url) == LinkKind.YOUTUBE &&
            (url.contains("list=") || url.contains("/playlist"))
    }

    fun enqueue(rawInput: String, options: EnqueueOptions) {
        val url = UrlClassifier.extractUrl(rawInput) ?: rawInput.trim()
        if (url.isBlank()) {
            _message.value = "Cole um link válido."
            return
        }
        when (UrlClassifier.classify(url)) {
            LinkKind.YOUTUBE -> enqueueYoutube(url, options)
            LinkKind.SPOTIFY -> enqueueSpotify(url, options)
            LinkKind.UNKNOWN -> _message.value = "Link não reconhecido (use YouTube ou Spotify)."
        }
    }

    private fun enqueueYoutube(url: String, options: EnqueueOptions) {
        viewModelScope.launch {
            InitManager.ensureInitialized(getApplication())
            if (options.wholePlaylist && isYoutubePlaylist(url)) {
                val entries = YoutubeDlDownloader.listPlaylist(url)
                if (entries.isEmpty()) {
                    _message.value = "Não consegui listar a playlist do YouTube."
                    return@launch
                }
                val folder = YoutubeDlDownloader.fetchTitle(url) ?: "Playlist"
                DownloadRepository.enqueue(
                    entries.map { entry ->
                        newRequest(
                            source = entry.url,
                            displayUrl = entry.url,
                            kind = LinkKind.YOUTUBE,
                            options = options,
                            presetTitle = entry.title,
                            subDir = folder,
                        )
                    },
                )
            } else {
                DownloadRepository.enqueue(
                    listOf(newRequest(url, url, LinkKind.YOUTUBE, options, presetTitle = "")),
                )
            }
        }
    }

    private fun enqueueSpotify(url: String, options: EnqueueOptions) {
        if (!spotifyAuth.isConfigured) {
            _message.value = "Spotify não configurado: adicione SPOTIFY_CLIENT_ID/SECRET."
            return
        }
        viewModelScope.launch {
            InitManager.ensureInitialized(getApplication())
            try {
                val resolution = spotifyResolver.resolve(url)
                if (resolution.tracks.isEmpty()) {
                    _message.value = "Nenhuma faixa encontrada nesse link do Spotify."
                    return@launch
                }
                val subDir = resolution.collectionName
                val audioOptions = options.copy(format = DownloadFormat.AUDIO_MP3)
                DownloadRepository.enqueue(
                    resolution.tracks.map { track ->
                        newRequest(
                            source = SpotifyToYoutubeBridge.searchSource(track),
                            displayUrl = url,
                            kind = LinkKind.SPOTIFY,
                            options = audioOptions,
                            presetTitle = track.searchQuery(),
                            subDir = subDir,
                            meta = TrackMeta(
                                title = track.title,
                                artist = track.artist,
                                album = track.album ?: subDir,
                                coverUrl = track.coverUrl,
                            ),
                        )
                    },
                )
            } catch (t: Throwable) {
                _message.value = t.message ?: "Falha ao resolver o link do Spotify."
            }
        }
    }

    private fun newRequest(
        source: String,
        displayUrl: String,
        kind: LinkKind,
        options: EnqueueOptions,
        presetTitle: String,
        subDir: String? = null,
        meta: TrackMeta? = null,
    ) = DownloadRequest(
        id = UUID.randomUUID().toString(),
        source = source,
        displayUrl = displayUrl,
        kind = kind,
        format = options.format,
        bitrate = options.bitrate,
        subtitles = options.subtitles,
        trimStart = options.trimStart,
        trimEnd = options.trimEnd,
        subDir = subDir,
        presetTitle = presetTitle,
        meta = meta,
    )

    fun preview(rawInput: String) {
        val url = UrlClassifier.extractUrl(rawInput) ?: rawInput.trim()
        if (url.isBlank()) {
            _message.value = "Cole um link válido."
            return
        }
        viewModelScope.launch {
            _previewLoading.value = true
            try {
                InitManager.ensureInitialized(getApplication())
                when (UrlClassifier.classify(url)) {
                    LinkKind.SPOTIFY -> {
                        if (!spotifyAuth.isConfigured) {
                            _message.value = "Spotify não configurado."
                            return@launch
                        }
                        val r = spotifyResolver.resolve(url)
                        val first = r.tracks.firstOrNull()
                        _preview.value = PreviewInfo(
                            title = r.collectionName ?: first?.searchQuery() ?: url,
                            subtitle = "${r.tracks.size} faixa(s) · Spotify",
                            thumbnail = first?.coverUrl,
                        )
                    }
                    LinkKind.YOUTUBE -> {
                        val info = YoutubeDlDownloader.fetchInfo(url)
                        if (info == null) {
                            _message.value = "Não consegui obter a prévia."
                        } else {
                            _preview.value = PreviewInfo(
                                title = info.title,
                                subtitle = formatDuration(info.durationSec),
                                thumbnail = info.thumbnail,
                            )
                        }
                    }
                    LinkKind.UNKNOWN -> _message.value = "Link não reconhecido."
                }
            } catch (t: Throwable) {
                _message.value = t.message ?: "Falha na prévia."
            } finally {
                _previewLoading.value = false
            }
        }
    }

    fun updateEngine() {
        viewModelScope.launch {
            _message.value = "Atualizando motor de download…"
            val ok = InitManager.updateEngine(getApplication())
            _message.value = if (ok) "Motor atualizado ✓" else "Não foi possível atualizar agora."
        }
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            _update.value = UpdateChecker.check(httpClient, BuildConfig.VERSION_NAME)
        }
    }

    fun cancel(task: DownloadTask) = DownloadRepository.cancel(task.id)
    fun remove(task: DownloadTask) = DownloadRepository.remove(task.id)
    fun retry(task: DownloadTask) = DownloadRepository.retry(task.id)
    fun clearFinished() = DownloadRepository.clearFinished()
    fun clearHistory() = HistoryStore.clear()

    private fun formatDuration(sec: Int): String {
        if (sec <= 0) return "YouTube"
        val m = sec / 60
        val s = sec % 60
        return "%d:%02d · YouTube".format(m, s)
    }
}
