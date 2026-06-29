package com.pokp.pokedex.domain

/** The 18 Pokémon types. */
enum class PokemonType(val displayName: String) {
    NORMAL("Normal"),
    FIRE("Fogo"),
    WATER("Água"),
    ELECTRIC("Elétrico"),
    GRASS("Planta"),
    ICE("Gelo"),
    FIGHTING("Lutador"),
    POISON("Veneno"),
    GROUND("Terra"),
    FLYING("Voador"),
    PSYCHIC("Psíquico"),
    BUG("Inseto"),
    ROCK("Pedra"),
    GHOST("Fantasma"),
    DRAGON("Dragão"),
    DARK("Sombrio"),
    STEEL("Aço"),
    FAIRY("Fada");

    companion object {
        /** Maps a PokeAPI type name (e.g. "fire") to a [PokemonType], or null if unknown. */
        fun fromApiName(name: String): PokemonType? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
