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
import kotlinx.coroutines.withContext

sealed interface InitState {
    data object Initializing : InitState
    data object Ready : InitState
    data class Failed(val message: String) : InitState
}

/**
 * Unpacks the bundled Python/yt-dlp/ffmpeg/aria2 native environment exactly once and
 * keeps yt-dlp up to date. The first init is slow (seconds), so the UI gates on [state].
 */
object InitManager {
    private const val TAG = "InitManager"

    private val _state = MutableStateFlow<InitState>(InitState.Initializing)
    val state: StateFlow<InitState> = _state.asStateFlow()

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
            _state.value = InitState.Ready
            // Keep extractors fresh; failure here is non-fatal (we still have a bundled yt-dlp).
            runCatching {
                YoutubeDL.getInstance()
                    .updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
            }.onFailure { Log.w(TAG, "yt-dlp update skipped: ${it.message}") }
        } catch (t: Throwable) {
            Log.e(TAG, "Init failed", t)
            _state.value = InitState.Failed(t.message ?: "Falha ao inicializar")
        }
    }
}
