package com.pokp.pokedex.data.seed

import kotlinx.serialization.Serializable

/**
 * Serializable representation of the full Pokédex dataset. This is the format of the
 * bundled `assets/pokedex.json` seed AND the in-memory structure the network sync builds
 * from PokeAPI responses, so a single import path populates the database in both cases.
 */
@Serializable
data class SeedBundle(
    val version: Int = 1,
    val pokemon: List<SeedPokemon> = emptyList(),
    val moves: List<SeedMove> = emptyList(),
    val evolutionChains: List<SeedEvolutionChain> = emptyList(),
)

@Serializable
data class SeedPokemon(
    val id: Int,
    val name: String,
    val types: List<String>,
    val generation: Int,
    val height: Int,
    val weight: Int,
    val baseStats: SeedStats,
    val abilities: List<String> = emptyList(),
    val flavorText: String = "",
    val evolutionChainId: Int = 0,
    val moves: List<SeedMoveLearn> = emptyList(),
)

@Serializable
data class SeedStats(
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val spAttack: Int,
    val spDefense: Int,
    val speed: Int,
)

@Serializable
data class SeedMoveLearn(
    val name: String,
    val level: Int = 0,
    val method: String = "other",
)

@Serializable
data class SeedMove(
    val name: String,
    val displayName: String = name,
    val type: String? = null,
    val power: Int? = null,
    val accuracy: Int? = null,
    val pp: Int? = null,
    val damageClass: String? = null,
)

@Serializable
data class SeedEvolutionChain(
    val id: Int,
    val nodes: List<SeedEvoNode> = emptyList(),
)

@Serializable
data class SeedEvoNode(
    val id: Int,
    val name: String,
    val evolvesFromId: Int? = null,
    val condition: String = "",
)
