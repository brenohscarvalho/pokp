package com.pokp.pokedex.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Pokémon row. List-valued data is stored as CSV (types/abilities) or JSON (moves) to
 * keep the schema flat; the repository handles (de)serialization.
 */
@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val generation: Int,
    val height: Int,
    val weight: Int,
    val baseHp: Int,
    val baseAttack: Int,
    val baseDefense: Int,
    val baseSpAttack: Int,
    val baseSpDefense: Int,
    val baseSpeed: Int,
    val typesCsv: String,
    val abilitiesCsv: String,
    val flavorText: String,
    val evolutionChainId: Int,
    val movesJson: String,
)

/** Projection used by the list screen to avoid loading the heavy [PokemonEntity.movesJson]. */
data class PokemonSummaryEntity(
    val id: Int,
    val name: String,
    @ColumnInfo(name = "typesCsv") val typesCsv: String,
    val generation: Int,
)

/** Shared move dictionary (move details independent of any Pokémon). */
@Entity(tableName = "moves")
data class MoveEntity(
    @PrimaryKey val name: String,
    val displayName: String,
    val type: String?,
    val power: Int?,
    val accuracy: Int?,
    val pp: Int?,
    val damageClass: String?,
)

/** An evolution chain, with its nodes stored as JSON. */
@Entity(tableName = "evolution_chains")
data class EvolutionChainEntity(
    @PrimaryKey val id: Int,
    val nodesJson: String,
)
