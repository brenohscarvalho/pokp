package com.pokp.pokedex.domain.team

import com.pokp.pokedex.domain.PokemonType
import com.pokp.pokedex.domain.TypeChart
import kotlin.math.ceil

/** A team member reduced to what the analyzer needs. */
data class AnalyzedMember(
    val name: String,
    val types: List<PokemonType>,
    /** Types of the member's damaging moves. */
    val attackTypes: Set<PokemonType>,
    /** For each attacking type the member could learn, an example damaging move name. */
    val learnableByType: Map<PokemonType, String>,
)

/** A candidate Pokémon used when suggesting additions. */
data class SuggestionCandidate(val name: String, val types: List<PokemonType>)

/** How many team members are weak/resistant/immune/neutral to one attacking type. */
data class TeamDefenseRow(
    val type: PokemonType,
    val weak: Int,
    val resist: Int,
    val immune: Int,
    val neutral: Int,
)

data class TeamSuggestion(val title: String, val detail: String)

data class TeamAnalysis(
    val defense: List<TeamDefenseRow>,
    val covered: Set<PokemonType>,
    val uncovered: Set<PokemonType>,
    val suggestions: List<TeamSuggestion>,
)

object TeamAnalyzer {

    fun analyze(
        members: List<AnalyzedMember>,
        candidates: List<SuggestionCandidate> = emptyList(),
    ): TeamAnalysis {
        if (members.isEmpty()) {
            return TeamAnalysis(emptyList(), emptySet(), emptySet(), emptyList())
        }

        val defense = PokemonType.entries.map { attacking ->
            var weak = 0; var resist = 0; var immune = 0; var neutral = 0
            members.forEach { m ->
                when (val mult = TypeChart.multiplierAgainst(attacking, m.types)) {
                    0.0 -> immune++
                    1.0 -> neutral++
                    else -> if (mult > 1.0) weak++ else resist++
                }
            }
            TeamDefenseRow(attacking, weak, resist, immune, neutral)
        }

        val teamAttackTypes = members.flatMap { it.attackTypes }.toSet()
        val covered = PokemonType.entries.filter { d ->
            teamAttackTypes.any { mt -> TypeChart.multiplier(mt, d) >= 2.0 }
        }.toSet()
        val uncovered = PokemonType.entries.toSet() - covered

        val suggestions = buildSuggestions(members, defense, teamAttackTypes, uncovered, candidates)

        return TeamAnalysis(defense, covered, uncovered, suggestions)
    }

    private fun buildSuggestions(
        members: List<AnalyzedMember>,
        defense: List<TeamDefenseRow>,
        teamAttackTypes: Set<PokemonType>,
        uncovered: Set<PokemonType>,
        candidates: List<SuggestionCandidate>,
    ): List<TeamSuggestion> {
        val suggestions = mutableListOf<TeamSuggestion>()
        val size = members.size
        val threshold = maxOf(2, ceil(size / 2.0).toInt())
        val onTeam = members.map { it.name }.toSet()

        // Defensive: shared weaknesses + Pokémon that would shore them up.
        val sharedWeak = defense
            .filter { it.weak >= threshold }
            .sortedByDescending { it.weak }
            .take(3)

        sharedWeak.forEach { row ->
            val resisters = candidates
                .asSequence()
                .filter { it.name !in onTeam }
                .filter { TypeChart.multiplierAgainst(row.type, it.types) < 1.0 }
                .sortedByDescending { cand ->
                    // Prefer Pokémon that also resist the team's other shared weaknesses.
                    sharedWeak.count { TypeChart.multiplierAgainst(it.type, cand.types) < 1.0 }
                }
                .map { it.name }
                .distinct()
                .take(4)
                .toList()
            val detail = if (resisters.isEmpty()) {
                "${row.weak} membros são fracos contra ${row.type.displayName}. " +
                    "Considere adicionar um Pokémon que resista a esse tipo."
            } else {
                "${row.weak} membros são fracos contra ${row.type.displayName}. " +
                    "Exemplos que resistem: ${resisters.joinToString(", ")}."
            }
            suggestions += TeamSuggestion("Fraqueza compartilhada: ${row.type.displayName}", detail)
        }

        // Offensive: fill coverage gaps with a move some member can already learn.
        if (uncovered.isNotEmpty()) {
            data class Option(
                val member: String,
                val type: PokemonType,
                val move: String,
                val newlyCovered: Set<PokemonType>,
            )

            val options = members.flatMap { m ->
                m.learnableByType
                    .filterKeys { it !in teamAttackTypes }
                    .map { (type, move) ->
                        val newly = uncovered.filter { d -> TypeChart.multiplier(type, d) >= 2.0 }.toSet()
                        Option(m.name, type, move, newly)
                    }
            }.filter { it.newlyCovered.isNotEmpty() }
                .sortedByDescending { it.newlyCovered.size }

            val usedTypes = mutableSetOf<PokemonType>()
            options.forEach { opt ->
                if (suggestions.size < 6 && opt.type !in usedTypes) {
                    usedTypes += opt.type
                    val targets = opt.newlyCovered.joinToString(", ") { it.displayName }
                    suggestions += TeamSuggestion(
                        "Melhorar cobertura: ${opt.type.displayName}",
                        "Ensine ${opt.move} em ${opt.member} para cobrir: $targets.",
                    )
                }
            }

            if (suggestions.none { it.title.startsWith("Melhorar cobertura") }) {
                suggestions += TeamSuggestion(
                    "Lacunas de cobertura",
                    "Tipos sem golpe super eficaz: " +
                        uncovered.joinToString(", ") { it.displayName } + ".",
                )
            }
        }

        if (suggestions.isEmpty()) {
            suggestions += TeamSuggestion(
                "Time equilibrado",
                "Sem fraquezas compartilhadas graves e a cobertura ofensiva está boa. Bom trabalho!",
            )
        }

        return suggestions
    }
}
