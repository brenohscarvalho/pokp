package com.pokp.pokedex

import com.pokp.pokedex.domain.PokemonType
import com.pokp.pokedex.domain.team.AnalyzedMember
import com.pokp.pokedex.domain.team.ShowdownFormat
import com.pokp.pokedex.domain.team.StatSpread
import com.pokp.pokedex.domain.team.TeamAnalyzer
import com.pokp.pokedex.domain.team.TeamMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamLogicTest {

    @Test
    fun showdown_round_trip_preserves_core_fields() {
        val member = TeamMember(
            speciesId = 6,
            speciesName = "Charizard",
            item = "Heavy-Duty Boots",
            ability = "Blaze",
            nature = "Timid",
            teraType = "Fire",
            evs = StatSpread(hp = 4, spa = 252, spe = 252),
            moves = listOf("Flamethrower", "Air Slash", "Roost", "Defog"),
        )

        val text = ShowdownFormat.export(listOf(member))
        val parsed = ShowdownFormat.parse(text)

        assertEquals(1, parsed.size)
        val p = parsed.first()
        assertEquals("Charizard", p.speciesName)
        assertEquals("Heavy-Duty Boots", p.item)
        assertEquals("Blaze", p.ability)
        assertEquals("Timid", p.nature)
        assertEquals("Fire", p.teraType)
        assertEquals(252, p.evs.spa)
        assertEquals(252, p.evs.spe)
        assertEquals(4, p.evs.hp)
        assertEquals(listOf("Flamethrower", "Air Slash", "Roost", "Defog"), p.moves)
    }

    @Test
    fun parses_minimal_block() {
        val text = """
            Pikachu @ Light Ball
            Ability: Static
            - Thunderbolt
        """.trimIndent()

        val parsed = ShowdownFormat.parse(text)
        assertEquals(1, parsed.size)
        assertEquals("Pikachu", parsed[0].speciesName)
        assertEquals("Light Ball", parsed[0].item)
        assertEquals(listOf("Thunderbolt"), parsed[0].moves)
    }

    @Test
    fun analyzer_flags_charizard_rock_weakness_and_coverage() {
        val charizard = AnalyzedMember(
            name = "Charizard",
            types = listOf(PokemonType.FIRE, PokemonType.FLYING),
            attackTypes = setOf(PokemonType.FIRE),
            learnableByType = mapOf(PokemonType.FIRE to "Flamethrower"),
        )

        val analysis = TeamAnalyzer.analyze(listOf(charizard))

        val rock = analysis.defense.first { it.type == PokemonType.ROCK }
        assertEquals(1, rock.weak)
        // Fire is super-effective against Grass; Water is not covered by Fire.
        assertTrue(PokemonType.GRASS in analysis.covered)
        assertTrue(PokemonType.WATER in analysis.uncovered)
        assertTrue(analysis.suggestions.isNotEmpty())
    }
}
