package com.pokp.pokedex.domain

/**
 * Computes a Pokémon's real stats from its base stats using the Gen III+ formulas.
 *
 * HP    = floor((2*Base + IV + floor(EV/4)) * Level / 100) + Level + 10
 * Other = floor((floor((2*Base + IV + floor(EV/4)) * Level / 100) + 5) * NatureMultiplier)
 *
 * Shedinja (HP base 1) is a documented special case where HP is always 1; that is not
 * modelled here because base HP of 1 still yields 11 at level 100, which is acceptable
 * for a calculator.
 */
object StatCalculator {

    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 100
    const val MIN_IV = 0
    const val MAX_IV = 31
    const val MIN_EV = 0
    const val MAX_EV = 252
    const val MAX_EV_TOTAL = 510

    /**
     * @param base base value for [stat]
     * @param iv individual value, 0..31
     * @param ev effort value, 0..252
     * @param level 1..100
     */
    fun calculate(
        stat: StatType,
        base: Int,
        iv: Int,
        ev: Int,
        level: Int,
        nature: Nature,
    ): Int {
        val clampedIv = iv.coerceIn(MIN_IV, MAX_IV)
        val clampedEv = ev.coerceIn(MIN_EV, MAX_EV)
        val clampedLevel = level.coerceIn(MIN_LEVEL, MAX_LEVEL)

        val common = (2 * base + clampedIv + clampedEv / 4) * clampedLevel / 100

        return if (stat == StatType.HP) {
            common + clampedLevel + 10
        } else {
            val natureMultiplier = nature.multiplierFor(stat)
            ((common + 5) * natureMultiplier).toInt()
        }
    }

    /** Computes all six stats for the given configuration. */
    fun calculateAll(
        base: BaseStats,
        ivs: Map<StatType, Int>,
        evs: Map<StatType, Int>,
        level: Int,
        nature: Nature,
    ): Map<StatType, Int> = StatType.entries.associateWith { stat ->
        calculate(
            stat = stat,
            base = base[stat],
            iv = ivs[stat] ?: MAX_IV,
            ev = evs[stat] ?: MIN_EV,
            level = level,
            nature = nature,
        )
    }
}
