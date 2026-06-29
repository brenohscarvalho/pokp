package com.pokp.pokedex

import com.pokp.pokedex.domain.Nature
import com.pokp.pokedex.domain.StatCalculator
import com.pokp.pokedex.domain.StatType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reference values cross-checked against Garchomp (Atk base 130, HP base 108) at level 100. */
class StatCalculatorTest {

    @Test
    fun attack_neutral_31iv_0ev_level100() {
        val result = StatCalculator.calculate(
            stat = StatType.ATTACK, base = 130, iv = 31, ev = 0, level = 100, nature = Nature.HARDY,
        )
        assertEquals(296, result)
    }

    @Test
    fun attack_adamant_31iv_252ev_level100() {
        val result = StatCalculator.calculate(
            stat = StatType.ATTACK, base = 130, iv = 31, ev = 252, level = 100, nature = Nature.ADAMANT,
        )
        assertEquals(394, result)
    }

    @Test
    fun attack_decreased_nature_applies_0_9() {
        val result = StatCalculator.calculate(
            stat = StatType.ATTACK, base = 130, iv = 31, ev = 0, level = 100, nature = Nature.BOLD,
        )
        // 296 * 0.9 = 266.4 -> floor 266
        assertEquals(266, result)
    }

    @Test
    fun hp_uses_dedicated_formula() {
        val result = StatCalculator.calculate(
            stat = StatType.HP, base = 108, iv = 31, ev = 0, level = 100, nature = Nature.HARDY,
        )
        assertEquals(357, result)
    }

    @Test
    fun hp_ignores_nature() {
        val withNature = StatCalculator.calculate(
            stat = StatType.HP, base = 108, iv = 31, ev = 0, level = 100, nature = Nature.BOLD,
        )
        assertEquals(357, withNature)
    }

    @Test
    fun out_of_range_values_are_clamped() {
        val clamped = StatCalculator.calculate(
            stat = StatType.SPEED, base = 100, iv = 999, ev = 999, level = 0, nature = Nature.HARDY,
        )
        val expected = StatCalculator.calculate(
            stat = StatType.SPEED, base = 100, iv = 31, ev = 252, level = 1, nature = Nature.HARDY,
        )
        assertEquals(expected, clamped)
    }
}
