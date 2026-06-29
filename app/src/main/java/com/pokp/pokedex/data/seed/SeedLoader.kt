package com.pokp.pokedex.data.seed

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Loads the bundled Pokédex dataset from `assets/pokedex.json`, if present. */
class SeedLoader(
    private val context: Context,
    private val json: Json,
) {
    /** Returns the bundled bundle, or null if the asset is missing or unreadable. */
    suspend fun load(): SeedBundle? = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.mapCatching { text ->
            json.decodeFromString<SeedBundle>(text)
        }.getOrNull()
    }

    companion object {
        const val ASSET_NAME = "pokedex.json"
    }
}
