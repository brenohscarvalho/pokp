package com.pokp.pokedex.domain

import com.pokp.pokedex.domain.PokemonType.*

/**
 * Standard (Gen VI+) type-effectiveness chart. Effectiveness is expressed as the damage
 * multiplier an *attacking* type deals to a *defending* type.
 */
object TypeChart {

    /** Only non-1.0 multipliers are listed; anything unlisted defaults to 1.0. */
    private val chart: Map<PokemonType, Map<PokemonType, Double>> = mapOf(
        NORMAL to mapOf(ROCK to 0.5, GHOST to 0.0, STEEL to 0.5),
        FIRE to mapOf(FIRE to 0.5, WATER to 0.5, GRASS to 2.0, ICE to 2.0, BUG to 2.0, ROCK to 0.5, DRAGON to 0.5, STEEL to 2.0),
        WATER to mapOf(FIRE to 2.0, WATER to 0.5, GRASS to 0.5, GROUND to 2.0, ROCK to 2.0, DRAGON to 0.5),
        ELECTRIC to mapOf(WATER to 2.0, ELECTRIC to 0.5, GRASS to 0.5, GROUND to 0.0, FLYING to 2.0, DRAGON to 0.5),
        GRASS to mapOf(FIRE to 0.5, WATER to 2.0, GRASS to 0.5, POISON to 0.5, GROUND to 2.0, FLYING to 0.5, BUG to 0.5, ROCK to 2.0, DRAGON to 0.5, STEEL to 0.5),
        ICE to mapOf(FIRE to 0.5, WATER to 0.5, GRASS to 2.0, ICE to 0.5, GROUND to 2.0, FLYING to 2.0, DRAGON to 2.0, STEEL to 0.5),
        FIGHTING to mapOf(NORMAL to 2.0, ICE to 2.0, POISON to 0.5, FLYING to 0.5, PSYCHIC to 0.5, BUG to 0.5, ROCK to 2.0, GHOST to 0.0, DARK to 2.0, STEEL to 2.0, FAIRY to 0.5),
        POISON to mapOf(GRASS to 2.0, POISON to 0.5, GROUND to 0.5, ROCK to 0.5, GHOST to 0.5, STEEL to 0.0, FAIRY to 2.0),
        GROUND to mapOf(FIRE to 2.0, ELECTRIC to 2.0, GRASS to 0.5, POISON to 2.0, FLYING to 0.0, BUG to 0.5, ROCK to 2.0, STEEL to 2.0),
        FLYING to mapOf(ELECTRIC to 0.5, GRASS to 2.0, FIGHTING to 2.0, BUG to 2.0, ROCK to 0.5, STEEL to 0.5),
        PSYCHIC to mapOf(FIGHTING to 2.0, POISON to 2.0, PSYCHIC to 0.5, DARK to 0.0, STEEL to 0.5),
        BUG to mapOf(FIRE to 0.5, GRASS to 2.0, FIGHTING to 0.5, POISON to 0.5, FLYING to 0.5, PSYCHIC to 2.0, GHOST to 0.5, DARK to 2.0, STEEL to 0.5, FAIRY to 0.5),
        ROCK to mapOf(FIRE to 2.0, ICE to 2.0, FIGHTING to 0.5, GROUND to 0.5, FLYING to 2.0, BUG to 2.0, STEEL to 0.5),
        GHOST to mapOf(NORMAL to 0.0, PSYCHIC to 2.0, GHOST to 2.0, DARK to 0.5),
        DRAGON to mapOf(DRAGON to 2.0, STEEL to 0.5, FAIRY to 0.0),
        DARK to mapOf(FIGHTING to 0.5, PSYCHIC to 2.0, GHOST to 2.0, DARK to 0.5, FAIRY to 0.5),
        STEEL to mapOf(FIRE to 0.5, WATER to 0.5, ELECTRIC to 0.5, ICE to 2.0, ROCK to 2.0, STEEL to 0.5, FAIRY to 2.0),
        FAIRY to mapOf(FIRE to 0.5, FIGHTING to 2.0, POISON to 0.5, DRAGON to 2.0, DARK to 2.0, STEEL to 0.5),
    )

    /** Multiplier that [attacking] deals when hitting a single [defending] type. */
    fun multiplier(attacking: PokemonType, defending: PokemonType): Double =
        chart[attacking]?.get(defending) ?: 1.0

    /**
     * Combined defensive multiplier for a Pokémon whose typing is [defenderTypes]
     * (one or two types) when hit by [attacking].
     */
    fun multiplierAgainst(attacking: PokemonType, defenderTypes: List<PokemonType>): Double =
        defenderTypes.fold(1.0) { acc, def -> acc * multiplier(attacking, def) }

    /**
     * Full defensive profile of a Pokémon: every attacking type mapped to its multiplier
     * against [defenderTypes].
     */
    fun defensiveProfile(defenderTypes: List<PokemonType>): Map<PokemonType, Double> =
        PokemonType.entries.associateWith { multiplierAgainst(it, defenderTypes) }
}
