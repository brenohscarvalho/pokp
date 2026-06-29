package com.pokp.pokedex.data.sync

import com.pokp.pokedex.data.Generation
import com.pokp.pokedex.data.remote.ChainLink
import com.pokp.pokedex.data.remote.EvolutionDetailDto
import com.pokp.pokedex.data.remote.PokeApiService
import com.pokp.pokedex.data.remote.PokemonDto
import com.pokp.pokedex.data.remote.SpeciesDto
import com.pokp.pokedex.data.seed.SeedBundle
import com.pokp.pokedex.data.seed.SeedEvoNode
import com.pokp.pokedex.data.seed.SeedEvolutionChain
import com.pokp.pokedex.data.seed.SeedMove
import com.pokp.pokedex.data.seed.SeedMoveLearn
import com.pokp.pokedex.data.seed.SeedPokemon
import com.pokp.pokedex.data.seed.SeedStats
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Progress update emitted during a sync. */
data class SyncProgress(val phase: String, val current: Int, val total: Int)

/**
 * Fetches the full Pokédex from PokeAPI and assembles it into a [SeedBundle]. The same
 * import path that loads the bundled asset then persists it, so the network sync and the
 * offline seed are interchangeable.
 */
class DataSyncManager(
    private val api: PokeApiService,
    private val concurrency: Int = 8,
) {
    /**
     * @param limit optional cap on the number of Pokémon (for testing); null = all.
     * @param onProgress invoked as work completes.
     */
    suspend fun fetchBundle(
        limit: Int? = null,
        onProgress: (SyncProgress) -> Unit,
    ): SeedBundle {
        val listing = api.getPokemonList(limit = limit ?: 100_000, offset = 0)
        val ids = listing.results.map { it.idFromUrl() }.filter { it > 0 }
            .let { if (limit != null) it.take(limit) else it }

        val semaphore = Semaphore(concurrency)
        var done = 0
        val total = ids.size

        val pokemonPairs: List<Pair<PokemonDto, SpeciesDto>> = coroutineScope {
            ids.map { id ->
                async {
                    semaphore.withPermit {
                        val result = runCatching {
                            val p = api.getPokemon(id)
                            val s = api.getSpecies(id)
                            p to s
                        }.getOrNull()
                        synchronized(this@DataSyncManager) {
                            done++
                            onProgress(SyncProgress("Pokémon", done, total))
                        }
                        result
                    }
                }
            }.mapNotNull { it.await() }
        }

        val pokemon = pokemonPairs.map { (p, s) -> toSeedPokemon(p, s) }

        // Evolution chains (unique).
        val chainUrls = pokemonPairs
            .mapNotNull { it.second.evolutionChain?.url }
            .filter { it.isNotBlank() }
            .distinct()
        var chainsDone = 0
        val chains: List<SeedEvolutionChain> = coroutineScope {
            chainUrls.map { url ->
                async {
                    semaphore.withPermit {
                        val chain = runCatching { api.getEvolutionChain(url) }.getOrNull()
                        synchronized(this@DataSyncManager) {
                            chainsDone++
                            onProgress(SyncProgress("Evoluções", chainsDone, chainUrls.size))
                        }
                        chain?.let { SeedEvolutionChain(it.id, flattenChain(it.chain)) }
                    }
                }
            }.mapNotNull { it.await() }
        }

        // Move details (only those referenced by some Pokémon).
        val moveNames = pokemon.flatMap { it.moves }.map { it.name }.distinct()
        var movesDone = 0
        val moves: List<SeedMove> = coroutineScope {
            moveNames.map { name ->
                async {
                    semaphore.withPermit {
                        val dto = runCatching { api.getMoveByName(name) }.getOrNull()
                        synchronized(this@DataSyncManager) {
                            movesDone++
                            onProgress(SyncProgress("Golpes", movesDone, moveNames.size))
                        }
                        dto?.let {
                            SeedMove(
                                name = it.name,
                                displayName = formatName(it.name),
                                type = it.type.name.ifBlank { null },
                                power = it.power,
                                accuracy = it.accuracy,
                                pp = it.pp,
                                damageClass = it.damageClass?.name,
                            )
                        }
                    }
                }
            }.mapNotNull { it.await() }
        }

        return SeedBundle(version = 1, pokemon = pokemon, moves = moves, evolutionChains = chains)
    }

    private fun toSeedPokemon(p: PokemonDto, s: SpeciesDto): SeedPokemon {
        val statsByName = p.stats.associate { it.stat.name to it.baseStat }
        val flavor = s.flavorTextEntries
            .firstOrNull { it.language.name == "en" }
            ?.flavorText
            ?.replace("\n", " ")
            ?.replace("", " ")
            ?.trim()
            .orEmpty()
        return SeedPokemon(
            id = p.id,
            name = formatName(p.name),
            types = p.types.sortedBy { it.slot }.map { it.type.name },
            generation = Generation.fromApiName(s.generation.name),
            height = p.height,
            weight = p.weight,
            baseStats = SeedStats(
                hp = statsByName["hp"] ?: 0,
                attack = statsByName["attack"] ?: 0,
                defense = statsByName["defense"] ?: 0,
                spAttack = statsByName["special-attack"] ?: 0,
                spDefense = statsByName["special-defense"] ?: 0,
                speed = statsByName["speed"] ?: 0,
            ),
            abilities = p.abilities.map { formatName(it.ability.name) },
            flavorText = flavor,
            evolutionChainId = s.evolutionChain?.url
                ?.trimEnd('/')?.substringAfterLast('/')?.toIntOrNull() ?: 0,
            moves = p.moves.mapNotNull { m ->
                val detail = m.versionGroupDetails.minByOrNull { it.levelLearnedAt }
                    ?: m.versionGroupDetails.firstOrNull()
                SeedMoveLearn(
                    name = m.move.name,
                    level = detail?.levelLearnedAt ?: 0,
                    method = detail?.moveLearnMethod?.name ?: "other",
                )
            },
        )
    }

    private fun flattenChain(root: ChainLink): List<SeedEvoNode> {
        val nodes = mutableListOf<SeedEvoNode>()
        fun walk(link: ChainLink, fromId: Int?) {
            val id = link.species.idFromUrl()
            val condition = if (fromId == null) "" else conditionText(link.evolutionDetails)
            nodes += SeedEvoNode(
                id = id,
                name = formatName(link.species.name),
                evolvesFromId = fromId,
                condition = condition,
            )
            link.evolvesTo.forEach { walk(it, id) }
        }
        walk(root, null)
        return nodes
    }

    private fun conditionText(details: List<EvolutionDetailDto>): String {
        val d = details.firstOrNull() ?: return "Especial"
        val parts = mutableListOf<String>()
        when {
            d.minLevel != null -> parts += "Nv. ${d.minLevel}"
            d.item != null -> parts += "Usar ${formatName(d.item.name)}"
            d.trigger.name == "trade" -> {
                parts += "Troca"
                d.heldItem?.let { parts += "segurando ${formatName(it.name)}" }
            }
            d.minHappiness != null -> parts += "Amizade alta"
            d.knownMove != null -> parts += "Conhecer ${formatName(d.knownMove.name)}"
            d.location != null -> parts += "Em ${formatName(d.location.name)}"
            d.heldItem != null -> parts += "Segurar ${formatName(d.heldItem.name)}"
            else -> parts += formatName(d.trigger.name.ifBlank { "especial" })
        }
        if (d.timeOfDay.isNotBlank()) parts += "(${d.timeOfDay})"
        return parts.joinToString(" ")
    }

    companion object {
        /** "charizard-mega-x" -> "Charizard Mega X". */
        fun formatName(raw: String): String = raw.split('-').joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
