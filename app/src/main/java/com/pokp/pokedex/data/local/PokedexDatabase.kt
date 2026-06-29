package com.pokp.pokedex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PokemonEntity::class,
        MoveEntity::class,
        EvolutionChainEntity::class,
        TeamEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PokedexDatabase : RoomDatabase() {
    abstract fun pokemonDao(): PokemonDao
    abstract fun moveDao(): MoveDao
    abstract fun evolutionDao(): EvolutionDao
    abstract fun teamDao(): TeamDao

    companion object {
        @Volatile
        private var instance: PokedexDatabase? = null

        fun get(context: Context): PokedexDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PokedexDatabase::class.java,
                    "pokedex.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
