package com.pokp.pokedex.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface PokeApiService {

    @GET("pokemon")
    suspend fun getPokemonList(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0,
    ): ResourceListDto

    @GET("pokemon/{id}")
    suspend fun getPokemon(@Path("id") id: Int): PokemonDto

    @GET("pokemon-species/{id}")
    suspend fun getSpecies(@Path("id") id: Int): SpeciesDto

    @GET("move/{id}")
    suspend fun getMove(@Path("id") id: Int): MoveDto

    @GET("move/{name}")
    suspend fun getMoveByName(@Path("name") name: String): MoveDto

    @GET("move")
    suspend fun getMoveList(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0,
    ): ResourceListDto

    /** Fetches an evolution chain by absolute url (the url is provided by the species). */
    @GET
    suspend fun getEvolutionChain(@Url url: String): EvolutionChainDto

    companion object {
        const val BASE_URL = "https://pokeapi.co/api/v2/"
    }
}
