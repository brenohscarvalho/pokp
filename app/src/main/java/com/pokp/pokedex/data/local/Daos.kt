package com.pokp.pokedex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonDao {

    @Query("SELECT id, name, typesCsv, generation FROM pokemon ORDER BY id ASC")
    fun observeSummaries(): Flow<List<PokemonSummaryEntity>>

    @Query("SELECT * FROM pokemon WHERE id = :id")
    suspend fun getById(id: Int): PokemonEntity?

    @Query("SELECT COUNT(*) FROM pokemon")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PokemonEntity>)
}

@Dao
interface MoveDao {

    @Query("SELECT * FROM moves WHERE name IN (:names)")
    suspend fun getByNames(names: List<String>): List<MoveEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MoveEntity>)
}

@Dao
interface EvolutionDao {

    @Query("SELECT * FROM evolution_chains WHERE id = :id")
    suspend fun getById(id: Int): EvolutionChainEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EvolutionChainEntity>)
}
