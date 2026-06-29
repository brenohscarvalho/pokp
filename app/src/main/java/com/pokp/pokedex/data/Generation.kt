package com.pokp.pokedex.data

/** Converts a PokeAPI generation name ("generation-iii") to its number (3). */
object Generation {
    private val roman = listOf(
        "ix" to 9, "viii" to 8, "vii" to 7, "vi" to 6, "v" to 5,
        "iv" to 4, "iii" to 3, "ii" to 2, "i" to 1,
    )

    fun fromApiName(name: String): Int {
        val suffix = name.removePrefix("generation-").lowercase()
        return roman.firstOrNull { it.first == suffix }?.second ?: 0
    }

    fun displayName(generation: Int): String =
        if (generation in 1..9) "Geração $generation" else "Desconhecida"
}
