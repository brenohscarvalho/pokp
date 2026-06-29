package com.pokp.pokedex.data.repository

import com.pokp.pokedex.data.local.EvolutionChainEntity
import com.pokp.pokedex.data.local.EvolutionDao
import com.pokp.pokedex.data.local.MoveDao
import com.pokp.pokedex.data.local.MoveEntity
import com.pokp.pokedex.data.local.PokemonDao
import com.pokp.pokedex.data.local.PokemonEntity
import com.pokp.pokedex.data.seed.SeedBundle
import com.pokp.pokedex.data.seed.SeedEvoNode
import com.pokp.pokedex.data.seed.SeedLoader
import com.pokp.pokedex.data.seed.SeedMoveLearn
import com.pokp.pokedex.data.sync.DataSyncManager
import com.pokp.pokedex.data.sync.SyncProgress
import com.pokp.pokedex.domain.BaseStats
import com.pokp.pokedex.domain.DamageClass
import com.pokp.pokedex.domain.EvolutionNode
import com.pokp.pokedex.domain.LearnMethod
import com.pokp.pokedex.domain.LearnedMove
import com.pokp.pokedex.domain.MoveInfo
import com.pokp.pokedex.domain.PokemonDetail
import com.pokp.pokedex.domain.PokemonSummary
import com.pokp.pokedex.domain.PokemonType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class PokedexRepository(
    private val pokemonDao: PokemonDao,
    private val moveDao: MoveDao,
    private val evolutionDao: EvolutionDao,
    private val seedLoader: SeedLoader,
    private val syncManager: DataSyncManager,
    private val json: Json,
) {
    fun observeSummaries(): Flow<List<PokemonSummary>> =
        pokemonDao.observeSummaries().map { rows ->
            rows.map { row ->
                PokemonSummary(
                    id = row.id,
                    name = row.name,
                    types = parseTypes(row.typesCsv),
                    generation = row.generation,
                )
            }
        }

    suspend fun isEmpty(): Boolean = pokemonDao.count() == 0

    suspend fun getDetail(id: Int): PokemonDetail? {
        val e = pokemonDao.getById(id) ?: return null
        val learns: List<SeedMoveLearn> = runCatching {
            json.decodeFromString<List<SeedMoveLearn>>(e.movesJson)
        }.getOrDefault(emptyList())

        val byName: Map<String, MoveEntity> =
            if (learns.isEmpty()) emptyMap()
            else moveDao.getByNames(learns.map { it.name }).associateBy { it.name }

        val moves = learns.map { learn ->
            val info = byName[learn.name].toMoveInfo(learn.name)
            LearnedMove(
                info = info,
                method = LearnMethod.fromApiName(learn.method),
                levelLearnedAt = learn.level,
            )
        }.sortedWith(
            compareBy({ it.method.ordinal }, { it.levelLearnedAt }, { it.info.displayName }),
        )

        return PokemonDetail(
            id = e.id,
            name = e.name,
            types = parseTypes(e.typesCsv),
            generation = e.generation,
            heightDecimetres = e.height,
            weightHectograms = e.weight,
            abilities = e.abilitiesCsv.split(',').filter { it.isNotBlank() },
            flavorText = e.flavorText,
            baseStats = BaseStats(
                hp = e.baseHp,
                attack = e.baseAttack,
                defense = e.baseDefense,
                spAttack = e.baseSpAttack,
                spDefense = e.baseSpDefense,
                speed = e.baseSpeed,
            ),
            evolutionChainId = e.evolutionChainId,
            moves = moves,
        )
    }

    suspend fun getEvolution(chainId: Int): List<EvolutionNode> {
        val entity = evolutionDao.getById(chainId) ?: return emptyList()
        val nodes = runCatching {
            json.decodeFromString<List<SeedEvoNode>>(entity.nodesJson)
        }.getOrDefault(emptyList())
        return nodes.map {
            EvolutionNode(
                id = it.id,
                name = it.name,
                evolvesFromId = it.evolvesFromId,
                condition = it.condition,
            )
        }
    }

    /** Seeds the database from the bundled asset if it exists and the DB is empty. */
    suspend fun seedFromAssetsIfNeeded(): Boolean {
        if (!isEmpty()) return false
        val bundle = seedLoader.load() ?: return false
        if (bundle.pokemon.isEmpty()) return false
        import(bundle)
        return true
    }

    /** Downloads the full dataset from PokeAPI and replaces local data. */
    suspend fun syncFromNetwork(
        limit: Int? = null,
        onProgress: (SyncProgress) -> Unit,
    ) {
        val bundle = syncManager.fetchBundle(limit = limit, onProgress = onProgress)
        import(bundle)
    }

    private suspend fun import(bundle: SeedBundle) {
        pokemonDao.upsertAll(
            bundle.pokemon.map { p ->
                PokemonEntity(
                    id = p.id,
                    name = p.name,
                    generation = p.generation,
                    height = p.height,
                    weight = p.weight,
                    baseHp = p.baseStats.hp,
                    baseAttack = p.baseStats.attack,
                    baseDefense = p.baseStats.defense,
                    baseSpAttack = p.baseStats.spAttack,
                    baseSpDefense = p.baseStats.spDefense,
                    baseSpeed = p.baseStats.speed,
                    typesCsv = p.types.joinToString(","),
                    abilitiesCsv = p.abilities.joinToString(","),
                    flavorText = p.flavorText,
                    evolutionChainId = p.evolutionChainId,
                    movesJson = json.encodeToString(p.moves),
                )
            },
        )
        moveDao.upsertAll(
            bundle.moves.map {
                MoveEntity(
                    name = it.name,
                    displayName = it.displayName,
                    type = it.type,
                    power = it.power,
                    accuracy = it.accuracy,
                    pp = it.pp,
                    damageClass = it.damageClass,
                )
            },
        )
        evolutionDao.upsertAll(
            bundle.evolutionChains.map {
                EvolutionChainEntity(id = it.id, nodesJson = json.encodeToString(it.nodes))
            },
        )
    }

    private fun parseTypes(csv: String): List<PokemonType> =
        csv.split(',').mapNotNull { PokemonType.fromApiName(it.trim()) }

    private fun MoveEntity?.toMoveInfo(fallbackName: String): MoveInfo =
        if (this == null) {
            MoveInfo(
                name = fallbackName,
                displayName = DataSyncManager.formatName(fallbackName),
                type = null,
                power = null,
                accuracy = null,
                pp = null,
                damageClass = DamageClass.UNKNOWN,
            )
        } else {
            MoveInfo(
                name = name,
                displayName = displayName,
                type = type?.let { PokemonType.fromApiName(it) },
                power = power,
                accuracy = accuracy,
                pp = pp,
                damageClass = DamageClass.fromApiName(damageClass),
            )
        }
}
