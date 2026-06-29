package com.pokp.pokedex.ui.theme

import androidx.compose.ui.graphics.Color
import com.pokp.pokedex.domain.PokemonType

val Crimson = Color(0xFFB71C1C)
val CrimsonDark = Color(0xFFEF5350)

/** Brand colour for each Pokémon type, used for chips and accents. */
val TypeColors: Map<PokemonType, Color> = mapOf(
    PokemonType.NORMAL to Color(0xFFA8A77A),
    PokemonType.FIRE to Color(0xFFEE8130),
    PokemonType.WATER to Color(0xFF6390F0),
    PokemonType.ELECTRIC to Color(0xFFF7D02C),
    PokemonType.GRASS to Color(0xFF7AC74C),
    PokemonType.ICE to Color(0xFF96D9D6),
    PokemonType.FIGHTING to Color(0xFFC22E28),
    PokemonType.POISON to Color(0xFFA33EA1),
    PokemonType.GROUND to Color(0xFFE2BF65),
    PokemonType.FLYING to Color(0xFFA98FF3),
    PokemonType.PSYCHIC to Color(0xFFF95587),
    PokemonType.BUG to Color(0xFFA6B91A),
    PokemonType.ROCK to Color(0xFFB6A136),
    PokemonType.GHOST to Color(0xFF735797),
    PokemonType.DRAGON to Color(0xFF6F35FC),
    PokemonType.DARK to Color(0xFF705746),
    PokemonType.STEEL to Color(0xFFB7B7CE),
    PokemonType.FAIRY to Color(0xFFD685AD),
)

fun colorForType(type: PokemonType): Color = TypeColors[type] ?: Color(0xFFA8A77A)
