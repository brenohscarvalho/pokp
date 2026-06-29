package com.pokp.pokedex.domain

/** The 18 Pokémon types. */
enum class PokemonType(val displayName: String) {
    NORMAL("Normal"),
    FIRE("Fire"),
    WATER("Water"),
    ELECTRIC("Electric"),
    GRASS("Grass"),
    ICE("Ice"),
    FIGHTING("Fighting"),
    POISON("Poison"),
    GROUND("Ground"),
    FLYING("Flying"),
    PSYCHIC("Psychic"),
    BUG("Bug"),
    ROCK("Rock"),
    GHOST("Ghost"),
    DRAGON("Dragon"),
    DARK("Dark"),
    STEEL("Steel"),
    FAIRY("Fairy");

    companion object {
        /** Maps a PokeAPI type name (e.g. "fire") to a [PokemonType], or null if unknown. */
        fun fromApiName(name: String): PokemonType? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
