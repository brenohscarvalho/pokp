package com.pokp.app.data

import android.content.Context
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface InitState {
    data object Initializing : InitState
    data object Ready : InitState
    data class Failed(val message: String) : InitState
}

sealed interface EngineUpdateState {
    data object Idle : EngineUpdateState
    data object Updating : EngineUpdateState
    data class Done(val version: String?) : EngineUpdateState
    data object Failed : EngineUpdateState
}

/**
 * Unpacks the bundled Python/yt-dlp/ffmpeg/aria2 native environment exactly once and
 * keeps yt-dlp up to date. The first init is slow (seconds), so the UI gates on [state].
 *
 * YouTube breaks old yt-dlp versions constantly (signature/nsig challenges), so keeping the
 * engine fresh is the single most important reliability lever — [updateState] tracks the
 * startup update so downloads can wait for it instead of running against a stale engine.
 */
object InitManager {
    private const val TAG = "InitManager"

    private val _state = MutableStateFlow<InitState>(InitState.Initializing)
    val state: StateFlow<InitState> = _state.asStateFlow()

    private val _updateState = MutableStateFlow<EngineUpdateState>(EngineUpdateState.Idle)
    val updateState: StateFlow<EngineUpdateState> = _updateState.asStateFlow()

    private val _engineVersion = MutableStateFlow<String?>(null)
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()

    @Volatile
    private var initialized = false

    suspend fun ensureInitialized(context: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        try {
            val appContext = context.applicationContext
            YoutubeDL.getInstance().init(appContext)
            FFmpeg.getInstance().init(appContext)
            Aria2c.getInstance().init(appContext)
            initialized = true
            _engineVersion.value = runCatching {
                YoutubeDL.getInstance().version(appContext)
            }.getOrNull()
            _state.value = InitState.Ready
            // Keep extractors fresh; failure here is non-fatal (we still have a bundled yt-dlp).
            updateEngine(appContext)
        } catch (t: Throwable) {
            Log.e(TAG, "Init failed", t)
            _state.value = InitState.Failed(t.message ?: "Falha ao inicializar")
        }
    }

    /** Updates the bundled yt-dlp. Returns true when the engine is confirmed fresh. */
    suspend fun updateEngine(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (_updateState.value == EngineUpdateState.Updating) {
            // Someone else is updating; just wait for the outcome.
            return@withContext awaitEngineUpdate()
        }
        _updateState.value = EngineUpdateState.Updating
        val appContext = context.applicationContext
        val ok = runCatching {
            YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
            true
        }.onFailure { Log.w(TAG, "yt-dlp update failed: ${it.message}") }.getOrDefault(false)
        _engineVersion.value = runCatching {
            YoutubeDL.getInstance().version(appContext)
        }.getOrNull()
        _updateState.value =
            if (ok) EngineUpdateState.Done(_engineVersion.value) else EngineUpdateState.Failed
        ok
    }

    /** Suspends until any in-flight engine update settles (max 60s). True if it succeeded. */
    suspend fun awaitEngineUpdate(): Boolean {
        val result = withTimeoutOrNull(60_000) {
            updateState.first { it !is EngineUpdateState.Updating && it !is EngineUpdateState.Idle }
        }
        return result is EngineUpdateState.Done
    }
}
