package com.pokp.app.data

import android.content.Context
import com.pokp.app.domain.DownloadTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HistoryEntry(
    val title: String,
    val kindName: String,
    val formatLabel: String,
    val savedUri: String?,
    val thumbnail: String?,
    val timestamp: Long,
)

/** Persists a rolling list of completed downloads. */
object HistoryStore {
    private const val PREFS = "pokp_history"
    private const val KEY = "entries"
    private const val MAX = 200

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var prefs: android.content.SharedPreferences

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _entries.value = runCatching {
            json.decodeFromString<List<HistoryEntry>>(prefs.getString(KEY, "[]")!!)
        }.getOrDefault(emptyList())
    }

    fun add(task: DownloadTask, timestamp: Long) {
        val entry = HistoryEntry(
            title = task.title.ifBlank { task.sourceUrl },
            kindName = task.kind.name,
            formatLabel = task.format.label,
            savedUri = task.savedUri,
            thumbnail = task.thumbnail,
            timestamp = timestamp,
        )
        val updated = (listOf(entry) + _entries.value).take(MAX)
        _entries.value = updated
        persist(updated)
    }

    fun clear() {
        _entries.value = emptyList()
        persist(emptyList())
    }

    private fun persist(list: List<HistoryEntry>) {
        runCatching { prefs.edit().putString(KEY, json.encodeToString(list)).apply() }
    }
}
