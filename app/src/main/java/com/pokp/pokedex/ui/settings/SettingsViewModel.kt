package com.pokp.pokedex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pokp.pokedex.data.SettingsPrefs
import com.pokp.pokedex.data.repository.PokedexRepository
import com.pokp.pokedex.data.sync.SyncProgress
import com.pokp.pokedex.ui.pokedexApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val syncing: Boolean = false,
    val progress: SyncProgress? = null,
    val message: String? = null,
)

class SettingsViewModel(
    private val repository: PokedexRepository,
    settingsPrefs: SettingsPrefs,
) : ViewModel() {

    val lastUpdated: StateFlow<Long> = settingsPrefs.lastUpdated.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0L,
    )

    private val prefs = settingsPrefs

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun updateData() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true, message = null, progress = null) }
        viewModelScope.launch {
            val result = runCatching {
                repository.syncFromNetwork { progress ->
                    _state.update { it.copy(progress = progress) }
                }
            }
            result
                .onSuccess {
                    prefs.setLastUpdated(System.currentTimeMillis())
                    _state.update {
                        it.copy(syncing = false, progress = null, message = "Update complete")
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            progress = null,
                            message = "Update failed: ${e.message ?: "network error"}",
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = pokedexApp()
                SettingsViewModel(
                    repository = app.container.repository,
                    settingsPrefs = app.container.settingsPrefs,
                )
            }
        }
    }
}
