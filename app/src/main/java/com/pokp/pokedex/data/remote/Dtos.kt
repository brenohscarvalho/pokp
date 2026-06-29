package com.pokp.pokedex.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NamedApiResource(
    val name: String = "",
    val url: String = "",
) {
    /** Extracts the trailing numeric id from a resource url like ".../pokemon/25/". */
    fun idFromUrl(): Int =
        url.trimEnd('/').substringAfterLast('/').toIntOrNull() ?: 0
}

@Serializable
data class ResourceListDto(
    val count: Int = 0,
    val results: List<NamedApiResource> = emptyList(),
)

@Serializable
data class PokemonDto(
    val id: Int = 0,
    val name: String = "",
    val height: Int = 0,
    val weight: Int = 0,
    val types: List<PokemonTypeSlot> = emptyList(),
    val stats: List<PokemonStatDto> = emptyList(),
    val abilities: List<PokemonAbilitySlot> = emptyList(),
    val moves: List<PokemonMoveDto> = emptyList(),
)

@Serializable
data class PokemonTypeSlot(
    val slot: Int = 0,
    val type: NamedApiResource = NamedApiResource(),
)

@Serializable
data class PokemonStatDto(
    @SerialName("base_stat") val baseStat: Int = 0,
    val stat: NamedApiResource = NamedApiResource(),
)

@Serializable
data class PokemonAbilitySlot(
    val ability: NamedApiResource = NamedApiResource(),
    @SerialName("is_hidden") val isHidden: Boolean = false,
)

@Serializable
data class PokemonMoveDto(
    val move: NamedApiResource = NamedApiResource(),
    @SerialName("version_group_details") val versionGroupDetails: List<MoveVersionDetail> = emptyList(),
)

@Serializable
data class MoveVersionDetail(
    @SerialName("level_learned_at") val levelLearnedAt: Int = 0,
    @SerialName("move_learn_method") val moveLearnMethod: NamedApiResource = NamedApiResource(),
)

@Serializable
data class SpeciesDto(
    val id: Int = 0,
    val name: String = "",
    val generation: NamedApiResource = NamedApiResource(),
    @SerialName("evolution_chain") val evolutionChain: EvolutionChainRef? = null,
    @SerialName("flavor_text_entries") val flavorTextEntries: List<FlavorTextEntry> = emptyList(),
)

@Serializable
data class EvolutionChainRef(val url: String = "")

@Serializable
data class FlavorTextEntry(
    @SerialName("flavor_text") val flavorText: String = "",
    val language: NamedApiResource = NamedApiResource(),
)

@Serializable
data class EvolutionChainDto(
    val id: Int = 0,
    val chain: ChainLink = ChainLink(),
)

@Serializable
data class ChainLink(
    val species: NamedApiResource = NamedApiResource(),
    @SerialName("evolution_details") val evolutionDetails: List<EvolutionDetailDto> = emptyList(),
    @SerialName("evolves_to") val evolvesTo: List<ChainLink> = emptyList(),
)

@Serializable
data class EvolutionDetailDto(
    val trigger: NamedApiResource = NamedApiResource(),
    @SerialName("min_level") val minLevel: Int? = null,
    @SerialName("min_happiness") val minHappiness: Int? = null,
    @SerialName("time_of_day") val timeOfDay: String = "",
    val item: NamedApiResource? = null,
    @SerialName("held_item") val heldItem: NamedApiResource? = null,
    @SerialName("known_move") val knownMove: NamedApiResource? = null,
    val location: NamedApiResource? = null,
)

@Serializable
data class MoveDto(
    val id: Int = 0,
    val name: String = "",
    val power: Int? = null,
    val accuracy: Int? = null,
    val pp: Int? = null,
    val type: NamedApiResource = NamedApiResource(),
    @SerialName("damage_class") val damageClass: NamedApiResource? = null,
)
