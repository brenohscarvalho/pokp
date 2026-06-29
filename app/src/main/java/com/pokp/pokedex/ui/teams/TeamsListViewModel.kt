package com.pokp.pokedex.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pokp.pokedex.data.repository.PokedexRepository
import com.pokp.pokedex.domain.team.Team
import com.pokp.pokedex.ui.pokedexApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeamsListViewModel(
    private val repository: PokedexRepository,
) : ViewModel() {

    val teams: StateFlow<List<Team>> = repository.observeTeams().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun deleteTeam(id: Long) {
        viewModelScope.launch { repository.deleteTeam(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TeamsListViewModel(pokedexApp().container.repository) }
        }
    }
}
