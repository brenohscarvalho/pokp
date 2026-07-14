package com.pokp.app.data

import android.content.Context
import com.pokp.app.domain.AudioBitrate
import com.pokp.app.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Simple persisted user preferences backed by SharedPreferences. */
object SettingsStore {
    private const val PREFS = "pokp_settings"

    private lateinit var prefs: android.content.SharedPreferences

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _bitrate = MutableStateFlow(AudioBitrate.BEST)
    val bitrate: StateFlow<AudioBitrate> = _bitrate.asStateFlow()

    private val _subtitles = MutableStateFlow(false)
    val subtitles: StateFlow<Boolean> = _subtitles.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        _dynamicColor.value = prefs.getBoolean("dynamic", true)
        _bitrate.value = runCatching {
            AudioBitrate.valueOf(prefs.getString("bitrate", AudioBitrate.BEST.name)!!)
        }.getOrDefault(AudioBitrate.BEST)
        _subtitles.value = prefs.getBoolean("subtitles", false)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit().putBoolean("dynamic", enabled).apply()
    }

    fun setBitrate(bitrate: AudioBitrate) {
        _bitrate.value = bitrate
        prefs.edit().putString("bitrate", bitrate.name).apply()
    }

    fun setSubtitles(enabled: Boolean) {
        _subtitles.value = enabled
        prefs.edit().putBoolean("subtitles", enabled).apply()
    }
}
