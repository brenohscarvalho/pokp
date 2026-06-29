package com.pokp.pokedex.domain

/** The six battle stats. */
enum class StatType(val displayName: String, val shortName: String) {
    HP("PV", "PV"),
    ATTACK("Ataque", "Atq"),
    DEFENSE("Defesa", "Def"),
    SP_ATTACK("Atq. Esp.", "AtE"),
    SP_DEFENSE("Def. Esp.", "DeE"),
    SPEED("Velocidade", "Vel");

    companion object {
        /** Maps a PokeAPI stat name (e.g. "special-attack") to a [StatType]. */
        fun fromApiName(name: String): StatType? = when (name) {
            "hp" -> HP
            "attack" -> ATTACK
            "defense" -> DEFENSE
            "special-attack" -> SP_ATTACK
            "special-defense" -> SP_DEFENSE
            "speed" -> SPEED
            else -> null
        }
    }
}

/** A Pokémon's six base stats. */
data class BaseStats(
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val spAttack: Int,
    val spDefense: Int,
    val speed: Int,
) {
    val total: Int get() = hp + attack + defense + spAttack + spDefense + speed

    operator fun get(stat: StatType): Int = when (stat) {
        StatType.HP -> hp
        StatType.ATTACK -> attack
        StatType.DEFENSE -> defense
        StatType.SP_ATTACK -> spAttack
        StatType.SP_DEFENSE -> spDefense
        StatType.SPEED -> speed
    }
}
