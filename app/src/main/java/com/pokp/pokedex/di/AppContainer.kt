package com.pokp.pokedex.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pokp.pokedex.data.SettingsPrefs
import com.pokp.pokedex.data.local.PokedexDatabase
import com.pokp.pokedex.data.remote.PokeApiService
import com.pokp.pokedex.data.repository.PokedexRepository
import com.pokp.pokedex.data.seed.SeedLoader
import com.pokp.pokedex.data.sync.DataSyncManager
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** Manual dependency container; created once in [com.pokp.pokedex.PokedexApp]. */
class AppContainer(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: PokeApiService = Retrofit.Builder()
        .baseUrl(PokeApiService.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PokeApiService::class.java)

    private val database = PokedexDatabase.get(context)

    private val seedLoader = SeedLoader(context, json)
    private val syncManager = DataSyncManager(api)

    val settingsPrefs = SettingsPrefs(context)

    val repository = PokedexRepository(
        pokemonDao = database.pokemonDao(),
        moveDao = database.moveDao(),
        evolutionDao = database.evolutionDao(),
        teamDao = database.teamDao(),
        seedLoader = seedLoader,
        syncManager = syncManager,
        json = json,
    )
}
