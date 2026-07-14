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
                    error = if (cancelled) null else (t.message ?: "Falha no download"),
                )
            }
        }
    }

    /** Retries transient network/DNS errors a few times with backoff before giving up. */
    private suspend fun downloadWithRetry(id: String, req: DownloadRequest): DownloadResult {
        val maxAttempts = 3
        var attempt = 0
        while (true) {
            try {
                return YoutubeDlDownloader.download(req, appContext.cacheDir) { progress, eta, _ ->
                    update(id) { it.copy(progress = progress, etaSeconds = eta) }
                }
            } catch (t: Throwable) {
                attempt++
                val cancelled = _tasks.value.firstOrNull { it.id == id }?.status ==
                    DownloadStatus.CANCELLED
                if (t is InterruptedException || cancelled || attempt >= maxAttempts ||
                    !isTransientNetworkError(t)
                ) {
                    throw t
                }
                update(id) {
                    it.copy(progress = 0f, error = "Rede instável, tentando novamente ($attempt)…")
                }
                delay(3000L * attempt)
                update(id) { it.copy(error = null) }
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

    private fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }
}
