package com.pokp.pokedex

import com.pokp.pokedex.domain.PokemonType
import com.pokp.pokedex.domain.TypeChart
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeChartTest {

    private val charizard = listOf(PokemonType.FIRE, PokemonType.FLYING)

    @Test
    fun single_type_super_effective() {
        assertEquals(2.0, TypeChart.multiplier(PokemonType.WATER, PokemonType.FIRE), 0.0)
    }

    @Test
    fun ghost_is_immune_to_normal() {
        assertEquals(0.0, TypeChart.multiplier(PokemonType.NORMAL, PokemonType.GHOST), 0.0)
    }

    @Test
    fun charizard_takes_quadruple_from_rock() {
        assertEquals(4.0, TypeChart.multiplierAgainst(PokemonType.ROCK, charizard), 0.0)
    }

    @Test
    fun charizard_is_immune_to_ground_via_flying() {
        assertEquals(0.0, TypeChart.multiplierAgainst(PokemonType.GROUND, charizard), 0.0)
    }

    @Test
    fun charizard_quarter_resists_grass() {
        assertEquals(0.25, TypeChart.multiplierAgainst(PokemonType.GRASS, charizard), 0.0)
    }

    @Test
    fun defensive_profile_covers_all_18_types() {
        val profile = TypeChart.defensiveProfile(charizard)
        assertEquals(18, profile.size)
        assertEquals(2.0, profile[PokemonType.WATER])
    }
}
