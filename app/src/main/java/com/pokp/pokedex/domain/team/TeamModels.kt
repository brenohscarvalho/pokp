package com.pokp.pokedex.domain.team

import com.pokp.pokedex.domain.StatType
import kotlinx.serialization.Serializable

/** A spread of the six stats (used for EVs and IVs). */
@Serializable
data class StatSpread(
    val hp: Int = 0,
    val atk: Int = 0,
    val def: Int = 0,
    val spa: Int = 0,
    val spd: Int = 0,
    val spe: Int = 0,
) {
    operator fun get(stat: StatType): Int = when (stat) {
        StatType.HP -> hp
        StatType.ATTACK -> atk
        StatType.DEFENSE -> def
        StatType.SP_ATTACK -> spa
        StatType.SP_DEFENSE -> spd
        StatType.SPEED -> spe
    }

    fun isAllZero(): Boolean = hp == 0 && atk == 0 && def == 0 && spa == 0 && spd == 0 && spe == 0

    companion object {
        val ZERO = StatSpread()
        val MAX_IVS = StatSpread(31, 31, 31, 31, 31, 31)
    }
}

/** A single Pokémon entry on a team. */
@Serializable
data class TeamMember(
    val speciesId: Int,
    val speciesName: String,
    val nickname: String? = null,
    val item: String? = null,
    val ability: String? = null,
    val level: Int = 100,
    val nature: String = "Hardy",
    val teraType: String? = null,
    val gender: String? = null,
    val shiny: Boolean = false,
    val evs: StatSpread = StatSpread.ZERO,
    val ivs: StatSpread = StatSpread.MAX_IVS,
    val moves: List<String> = emptyList(),
)

/** A full team. */
@Serializable
data class Team(
    val id: Long = 0L,
    val name: String = "",
    val members: List<TeamMember> = emptyList(),
)
