package com.pokp.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.pokp.app.domain.DownloadRequest
import com.pokp.app.domain.DownloadStatus
import com.pokp.app.domain.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Process-wide download queue. A single worker coroutine drains it sequentially so downloads
 * survive the Activity being recreated or the app being backgrounded (kept alive by
 * [DownloadService]). The UI observes [tasks].
 */
object DownloadRepository {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private var worker: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun enqueue(requests: List<DownloadRequest>) {
        if (requests.isEmpty()) return
        _tasks.update { current -> current + requests.map { DownloadTask(request = it) } }
        startService()
        ensureWorker()
    }

    fun cancel(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.status.isFinished) return
        update(id) { it.copy(status = DownloadStatus.CANCELLED, error = null) }
        scope.launch { YoutubeDlDownloader.cancel(id.replace("-", "")) }
    }

    fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    fun retry(id: String) {
        update(id) { it.copy(status = DownloadStatus.QUEUED, progress = 0f, error = null) }
        startService()
        ensureWorker()
    }

    fun clearFinished() {
        _tasks.update { list -> list.filterNot { it.status.isFinished } }
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(
                appContext, Intent(appContext, DownloadService::class.java),
            )
        }
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch { drainQueue() }
    }

    private suspend fun drainQueue() {
        while (true) {
            val next = _tasks.value.firstOrNull { it.status == DownloadStatus.QUEUED } ?: break
            processOne(next.id)
        }
    }

    private suspend fun processOne(id: String) {
        InitManager.ensureInitialized(appContext)
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.status == DownloadStatus.CANCELLED) return
        val req = task.request

        update(id) { it.copy(status = DownloadStatus.RESOLVING) }
        if (task.title.isBlank()) {
            YoutubeDlDownloader.fetchTitle(req.source)?.let { t -> update(id) { it.copy(title = t) } }
        }

        try {
            update(id) { it.copy(status = DownloadStatus.DOWNLOADING) }
            val result = downloadWithRetry(id, req)

            update(id) { it.copy(status = DownloadStatus.SAVING, progress = 100f) }
            if (result.isAudio && req.meta != null) {
                Mp3Tagger.tag(result.file, req.meta, httpClient)
            }
            val uri = MediaStoreSaver.save(
                context = appContext,
                file = result.file,
                mimeType = result.mimeType,
                isAudio = result.isAudio,
                subDir = req.subDir,
            )
            result.file.delete()
            update(id) { it.copy(status = DownloadStatus.DONE, savedUri = uri.toString()) }
            _tasks.value.firstOrNull { it.id == id }?.let {
                HistoryStore.add(it, System.currentTimeMillis())
            }
        } catch (t: Throwable) {
            val cancelled = t is InterruptedException ||
                _tasks.value.firstOrNull { it.id == id }?.status == DownloadStatus.CANCELLED
            update(id) {
                it.copy(
                    status = if (cancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED,
                    error = if (cancelled) null else shortError(t),
                )
            }
        }
    }

    /** Once per app session: on the first YouTube-blocked error we refresh the engine. */
    @Volatile
    private var engineRefreshedThisSession = false

    /**
     * Retry ladder:
     * 1. transient network/DNS errors → simple retry with backoff (up to 3x);
     * 2. YouTube blocking (403/signature/challenge) → update yt-dlp once, retry;
     * 3. still blocked → retry with the android player client (no JS challenges);
     * 4. give up with a readable error.
     */
    private suspend fun downloadWithRetry(id: String, req: DownloadRequest): DownloadResult {
        // Never start a download while a fresher engine is seconds away.
        if (InitManager.updateState.value is EngineUpdateState.Updating) {
            update(id) { it.copy(error = "Atualizando motor de download…") }
            InitManager.awaitEngineUpdate()
            update(id) { it.copy(error = null) }
        }

        val maxNetworkAttempts = 3
        var networkAttempt = 0
        var triedEngineUpdate = engineRefreshedThisSession
        var compatMode = false

        while (true) {
            try {
                return YoutubeDlDownloader.download(
                    req, appContext.cacheDir, compatMode,
                ) { progress, eta, _ ->
                    update(id) { it.copy(progress = progress, etaSeconds = eta) }
                }
            } catch (t: Throwable) {
                val cancelled = _tasks.value.firstOrNull { it.id == id }?.status ==
                    DownloadStatus.CANCELLED
                if (t is InterruptedException || cancelled) throw t

                when {
                    isTransientNetworkError(t) && networkAttempt < maxNetworkAttempts - 1 -> {
                        networkAttempt++
                        update(id) {
                            it.copy(
                                progress = 0f,
                                error = "Rede instável, tentando novamente ($networkAttempt)…",
                            )
                        }
                        delay(3000L * networkAttempt)
                        update(id) { it.copy(error = null) }
                    }

                    isYoutubeBlockedError(t) && !triedEngineUpdate -> {
                        triedEngineUpdate = true
                        engineRefreshedThisSession = true
                        update(id) {
                            it.copy(progress = 0f, error = "YouTube mudou — atualizando motor…")
                        }
                        InitManager.updateEngine(appContext)
                        update(id) { it.copy(error = null) }
                    }

                    isYoutubeBlockedError(t) && !compatMode -> {
                        compatMode = true
                        update(id) {
                            it.copy(progress = 0f, error = "Tentando modo alternativo…")
                        }
                    }

                    else -> throw t
                }
            }
        }
    }

    private fun isTransientNetworkError(t: Throwable): Boolean {
        val m = (t.message ?: "").lowercase()
        return listOf(
            "hostname", "errno 7", "temporary failure", "transporterror",
            "timed out", "timeout", "connection reset",
            "network is unreachable", "no address",
        ).any { it in m }
    }

    /** Signature/challenge failures caused by a stale yt-dlp vs. current YouTube player. */
    private fun isYoutubeBlockedError(t: Throwable): Boolean {
        val m = (t.message ?: "").lowercase()
        return listOf(
            "403", "forbidden", "signature", "challenge", "nsig", "n function",
            "sign in to confirm", "not a bot", "requested format is not available",
            "unable to download", "fragment",
        ).any { it in m }
    }

    /** yt-dlp dumps walls of log text into exceptions; keep only the meaningful tail. */
    private fun shortError(t: Throwable): String {
        val raw = (t.message ?: "Falha no download").trim()
        val line = raw.lines().lastOrNull { it.contains("ERROR", ignoreCase = true) }
            ?: raw.lines().lastOrNull { it.isNotBlank() }
            ?: raw
        return line.trim().take(300)
    }

    private fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }
}
