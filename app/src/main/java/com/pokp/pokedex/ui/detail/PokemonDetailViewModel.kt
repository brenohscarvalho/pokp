package com.pokp.pokedex.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pokp.pokedex.data.repository.PokedexRepository
import com.pokp.pokedex.domain.EvolutionNode
import com.pokp.pokedex.domain.Nature
import com.pokp.pokedex.domain.PokemonDetail
import com.pokp.pokedex.domain.PokemonType
import com.pokp.pokedex.domain.StatCalculator
import com.pokp.pokedex.domain.StatType
import com.pokp.pokedex.domain.TypeChart
import com.pokp.pokedex.ui.navigation.Routes
import com.pokp.pokedex.ui.pokedexApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalculatorState(
    val level: Int = 50,
    val nature: Nature = Nature.HARDY,
    val ivs: Map<StatType, Int> = StatType.entries.associateWith { StatCalculator.MAX_IV },
    val evs: Map<StatType, Int> = StatType.entries.associateWith { 0 },
)

data class DetailUiState(
    val loading: Boolean = true,
    val detail: PokemonDetail? = null,
    val evolutions: List<EvolutionNode> = emptyList(),
    val defense: Map<PokemonType, Double> = emptyMap(),
    val calculator: CalculatorState = CalculatorState(),
    val computedStats: Map<StatType, Int> = emptyMap(),
)

class PokemonDetailViewModel(
    private val repository: PokedexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pokemonId: Int = savedStateHandle.get<Int>(Routes.ARG_ID) ?: 1

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val detail = repository.getDetail(pokemonId)
            if (detail == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val evolutions = repository.getEvolution(detail.evolutionChainId)
            val defense = TypeChart.defensiveProfile(detail.types)
            _state.update {
                it.copy(
                    loading = false,
                    detail = detail,
                    evolutions = evolutions,
                    defense = defense,
                )
            }
            recompute()
        }
    }

    private fun recompute() {
        val detail = _state.value.detail ?: return
        val calc = _state.value.calculator
        val computed = StatCalculator.calculateAll(
            base = detail.baseStats,
            ivs = calc.ivs,
            evs = calc.evs,
            level = calc.level,
            nature = calc.nature,
        )
        _state.update { it.copy(computedStats = computed) }
    }

    fun setLevel(level: Int) {
        _state.update { it.copy(calculator = it.calculator.copy(level = level)) }
        recompute()
    }

    fun setNature(nature: Nature) {
        _state.update { it.copy(calculator = it.calculator.copy(nature = nature)) }
        recompute()
    }

    fun setIv(stat: StatType, value: Int) {
        _state.update {
            it.copy(calculator = it.calculator.copy(ivs = it.calculator.ivs + (stat to value)))
        }
        recompute()
    }

    fun setEv(stat: StatType, value: Int) {
        _state.update {
            it.copy(calculator = it.calculator.copy(evs = it.calculator.evs + (stat to value)))
        }
        recompute()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                PokemonDetailViewModel(
                    repository = pokedexApp().container.repository,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
