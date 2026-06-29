package com.pokp.pokedex.domain

/** Lightweight model for the list screen. */
data class PokemonSummary(
    val id: Int,
    val name: String,
    val types: List<PokemonType>,
    val generation: Int,
) {
    val spriteUrl: String get() = ImageUrls.sprite(id)
}

/** How a move is learned by a Pokémon. */
enum class LearnMethod(val displayName: String) {
    LEVEL_UP("Subir de nível"),
    MACHINE("MT/MO"),
    EGG("Ovo"),
    TUTOR("Tutor"),
    OTHER("Outro");

    companion object {
        fun fromApiName(name: String): LearnMethod = when (name) {
            "level-up" -> LEVEL_UP
            "machine" -> MACHINE
            "egg" -> EGG
            "tutor" -> TUTOR
            else -> OTHER
        }
    }
}

/** Damage category of a move. */
enum class DamageClass(val displayName: String) {
    PHYSICAL("Físico"),
    SPECIAL("Especial"),
    STATUS("Status"),
    UNKNOWN("—");

    companion object {
        fun fromApiName(name: String?): DamageClass = when (name) {
            "physical" -> PHYSICAL
            "special" -> SPECIAL
            "status" -> STATUS
            else -> UNKNOWN
        }
    }
}

/** Static information about a move (shared across Pokémon). */
data class MoveInfo(
    val name: String,
    val displayName: String,
    val type: PokemonType?,
    val power: Int?,
    val accuracy: Int?,
    val pp: Int?,
    val damageClass: DamageClass,
)

/** A move as learned by a particular Pokémon, joined with its [info]. */
data class LearnedMove(
    val info: MoveInfo,
    val method: LearnMethod,
    val levelLearnedAt: Int,
)

/** A node in an evolution chain. */
data class EvolutionNode(
    val id: Int,
    val name: String,
    val evolvesFromId: Int?,
    val condition: String,
) {
    val spriteUrl: String get() = ImageUrls.sprite(id)
}

/** Full detail model for the detail screen. */
data class PokemonDetail(
    val id: Int,
    val name: String,
    val types: List<PokemonType>,
    val generation: Int,
    val heightDecimetres: Int,
    val weightHectograms: Int,
    val abilities: List<String>,
    val flavorText: String,
    val baseStats: BaseStats,
    val evolutionChainId: Int,
    val moves: List<LearnedMove>,
) {
    val spriteUrl: String get() = ImageUrls.sprite(id)
    val artworkUrl: String get() = ImageUrls.artwork(id)
    val heightMeters: Double get() = heightDecimetres / 10.0
    val weightKilograms: Double get() = weightHectograms / 10.0
}
