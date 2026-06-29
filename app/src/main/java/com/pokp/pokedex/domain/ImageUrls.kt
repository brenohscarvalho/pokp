package com.pokp.pokedex.domain

/** Builds PokeAPI sprite/artwork URLs from a national dex id. */
object ImageUrls {
    private const val BASE =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon"

    /** Small pixel sprite (front default) — used in lists. */
    fun sprite(id: Int): String = "$BASE/$id.png"

    /** High-resolution official artwork — used on detail screens. */
    fun artwork(id: Int): String = "$BASE/other/official-artwork/$id.png"
}
