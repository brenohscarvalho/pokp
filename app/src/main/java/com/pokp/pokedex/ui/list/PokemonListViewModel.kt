package com.pokp.pokedex.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pokp.pokedex.data.repository.PokedexRepository
import com.pokp.pokedex.domain.PokemonSummary
import com.pokp.pokedex.domain.PokemonType
import com.pokp.pokedex.ui.pokedexApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListUiState(
    val loading: Boolean = true,
    val items: List<PokemonSummary> = emptyList(),
    val query: String = "",
    val typeFilter: PokemonType? = null,
    val generationFilter: Int? = null,
    val isEmpty: Boolean = false,
)

class PokemonListViewModel(
    private val repository: PokedexRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val typeFilter = MutableStateFlow<PokemonType?>(null)
    private val generationFilter = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ListUiState> = combine(
        repository.observeSummaries(),
        query,
        typeFilter,
        generationFilter,
    ) { all, q, type, gen ->
        val filtered = all.filter { p ->
            (q.isBlank() || p.name.contains(q, ignoreCase = true) || p.id.toString() == q.trim()) &&
                (type == null || type in p.types) &&
                (gen == null || p.generation == gen)
        }
        ListUiState(
            loading = false,
            items = filtered,
            query = q,
            typeFilter = type,
            generationFilter = gen,
            isEmpty = all.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListUiState(),
    )

    init {
        // Populate from the bundled asset on first launch (no-op if data already present).
        viewModelScope.launch { repository.seedFromAssetsIfNeeded() }
    }

    fun onQueryChange(value: String) { query.value = value }
    fun onTypeFilter(value: PokemonType?) { typeFilter.value = value }
    fun onGenerationFilter(value: Int?) { generationFilter.value = value }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                PokemonListViewModel(pokedexApp().container.repository)
            }
        }
    }
}
