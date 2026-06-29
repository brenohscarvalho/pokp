package com.pokp.pokedex.ui.teams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pokp.pokedex.data.repository.PokedexRepository
import com.pokp.pokedex.domain.DamageClass
import com.pokp.pokedex.domain.PokemonDetail
import com.pokp.pokedex.domain.PokemonSummary
import com.pokp.pokedex.domain.PokemonType
import com.pokp.pokedex.domain.team.AnalyzedMember
import com.pokp.pokedex.domain.team.ShowdownFormat
import com.pokp.pokedex.domain.team.SuggestionCandidate
import com.pokp.pokedex.domain.team.Team
import com.pokp.pokedex.domain.team.TeamAnalysis
import com.pokp.pokedex.domain.team.TeamAnalyzer
import com.pokp.pokedex.domain.team.TeamMember
import com.pokp.pokedex.ui.navigation.Routes
import com.pokp.pokedex.ui.pokedexApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_TEAM_SIZE = 6
const val MAX_MOVES = 4

sealed interface Overlay {
    data object None : Overlay
    data object AddMember : Overlay
    data class MemberOptions(val index: Int) : Overlay
    data class MovePicker(val index: Int, val slot: Int) : Overlay
    data object Import : Overlay
    data class Export(val text: String) : Overlay
}

data class TeamEditorState(
    val loading: Boolean = true,
    val id: Long = 0L,
    val name: String = "",
    val members: List<TeamMember> = emptyList(),
    val details: Map<Int, PokemonDetail> = emptyMap(),
    val analysis: TeamAnalysis? = null,
    val overlay: Overlay = Overlay.None,
    val message: String? = null,
)

class TeamEditorViewModel(
    private val repository: PokedexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val teamId: Long = savedStateHandle.get<Long>(Routes.ARG_TEAM_ID) ?: 0L

    private val _state = MutableStateFlow(TeamEditorState())
    val state: StateFlow<TeamEditorState> = _state.asStateFlow()

    val pickerQuery = MutableStateFlow("")
    val moveQuery = MutableStateFlow("")

    /** Filtered Pokémon list for the add-member picker. */
    val pickerResults: StateFlow<List<PokemonSummary>> =
        combine(repository.observeSummaries(), pickerQuery) { all, q ->
            if (q.isBlank()) all
            else all.filter { it.name.contains(q, true) || it.id.toString() == q.trim() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var candidates: List<SuggestionCandidate> = emptyList()

    init {
        viewModelScope.launch {
            candidates = repository.getAllSummaries()
                .map { SuggestionCandidate(it.name, it.types) }
            if (teamId != 0L) {
                val team = repository.getTeam(teamId)
                if (team != null) {
                    val details = team.members
                        .map { it.speciesId }.distinct()
                        .mapNotNull { id -> repository.getDetail(id)?.let { id to it } }
                        .toMap()
                    _state.update {
                        it.copy(id = team.id, name = team.name, members = team.members, details = details)
                    }
                }
            }
            _state.update { it.copy(loading = false) }
            recompute()
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun openOverlay(overlay: Overlay) {
        if (overlay is Overlay.AddMember) pickerQuery.value = ""
        if (overlay is Overlay.MovePicker) moveQuery.value = ""
        _state.update { it.copy(overlay = overlay) }
    }

    fun closeOverlay() = _state.update { it.copy(overlay = Overlay.None) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun addMember(speciesId: Int) {
        if (_state.value.members.size >= MAX_TEAM_SIZE) return
        viewModelScope.launch {
            val detail = repository.getDetail(speciesId) ?: return@launch
            val member = TeamMember(speciesId = detail.id, speciesName = detail.name)
            _state.update {
                it.copy(
                    members = it.members + member,
                    details = it.details + (detail.id to detail),
                    overlay = Overlay.None,
                )
            }
            recompute()
        }
    }

    fun removeMember(index: Int) {
        _state.update { it.copy(members = it.members.filterIndexed { i, _ -> i != index }) }
        recompute()
    }

    private fun mutateMember(index: Int, transform: (TeamMember) -> TeamMember) {
        _state.update { s ->
            s.copy(members = s.members.mapIndexed { i, m -> if (i == index) transform(m) else m })
        }
        recompute()
    }

    fun setNature(index: Int, nature: String) = mutateMember(index) { it.copy(nature = nature) }
    fun setItem(index: Int, item: String) =
        mutateMember(index) { it.copy(item = item.ifBlank { null }) }
    fun setAbility(index: Int, ability: String) =
        mutateMember(index) { it.copy(ability = ability.ifBlank { null }) }
    fun setLevel(index: Int, level: Int) =
        mutateMember(index) { it.copy(level = level.coerceIn(1, 100)) }
    fun setTera(index: Int, tera: String?) = mutateMember(index) { it.copy(teraType = tera) }

    fun setMove(index: Int, slot: Int, moveName: String) {
        mutateMember(index) { m ->
            val moves = m.moves.toMutableList()
            while (moves.size <= slot) moves.add("")
            moves[slot] = moveName
            m.copy(moves = moves.filter { it.isNotBlank() })
        }
        _state.update { it.copy(overlay = Overlay.MemberOptions(index)) }
    }

    fun clearMove(index: Int, slot: Int) = mutateMember(index) { m ->
        val moves = m.moves.toMutableList()
        if (slot < moves.size) moves.removeAt(slot)
        m.copy(moves = moves)
    }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            val newId = repository.saveTeam(Team(id = s.id, name = s.name, members = s.members))
            _state.update { it.copy(id = newId, message = "Time salvo") }
        }
    }

    fun buildExport() {
        val text = ShowdownFormat.export(_state.value.members)
        _state.update { it.copy(overlay = Overlay.Export(text)) }
    }

    fun import(text: String) {
        viewModelScope.launch {
            val parsed = ShowdownFormat.parse(text)
            if (parsed.isEmpty()) {
                _state.update { it.copy(overlay = Overlay.None, message = "Nada para importar") }
                return@launch
            }
            val summaries = repository.getAllSummaries().associateBy { normalize(it.name) }

            val resolved = mutableListOf<TeamMember>()
            val unresolved = mutableListOf<String>()
            parsed.take(MAX_TEAM_SIZE).forEach { m ->
                val match = summaries[normalize(m.speciesName)]
                if (match != null) {
                    resolved += m.copy(speciesId = match.id, speciesName = match.name)
                } else {
                    unresolved += m.speciesName
                }
            }

            val details = resolved.map { it.speciesId }.distinct()
                .mapNotNull { id -> repository.getDetail(id)?.let { id to it } }.toMap()

            val msg = if (unresolved.isEmpty()) {
                "Importados ${resolved.size} Pokémon"
            } else {
                "Importados ${resolved.size}; não encontrados: ${unresolved.joinToString(", ")}"
            }
            _state.update {
                it.copy(members = resolved, details = details, overlay = Overlay.None, message = msg)
            }
            recompute()
        }
    }

    private fun recompute() {
        val s = _state.value
        val analyzed = s.members.mapNotNull { member ->
            val detail = s.details[member.speciesId] ?: return@mapNotNull null
            val infoByNorm = detail.moves.associateBy { normalize(it.info.displayName) }
            val attackTypes = member.moves.mapNotNull { mv ->
                val info = infoByNorm[normalize(mv)]?.info
                if (info != null && info.damageClass != DamageClass.STATUS) info.type else null
            }.toSet()
            val learnableByType: Map<PokemonType, String> = detail.moves
                .filter { it.info.damageClass != DamageClass.STATUS && it.info.type != null }
                .groupBy { it.info.type!! }
                .mapValues { (_, list) ->
                    list.maxByOrNull { it.info.power ?: 0 }!!.info.displayName
                }
            AnalyzedMember(
                name = detail.name,
                types = detail.types,
                attackTypes = attackTypes,
                learnableByType = learnableByType,
            )
        }
        val analysis = TeamAnalyzer.analyze(analyzed, candidates)
        _state.update { it.copy(analysis = analysis) }
    }

    private fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                TeamEditorViewModel(
                    repository = pokedexApp().container.repository,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
