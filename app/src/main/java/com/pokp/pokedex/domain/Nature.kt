package com.pokp.pokedex.domain

/**
 * The 25 natures. Each raises one stat by 10% and lowers another by 10%.
 * Neutral natures have no effect ([increased] == [decreased] == null).
 */
enum class Nature(
    val displayName: String,
    val increased: StatType?,
    val decreased: StatType?,
) {
    HARDY("Hardy", null, null),
    LONELY("Lonely", StatType.ATTACK, StatType.DEFENSE),
    BRAVE("Brave", StatType.ATTACK, StatType.SPEED),
    ADAMANT("Adamant", StatType.ATTACK, StatType.SP_ATTACK),
    NAUGHTY("Naughty", StatType.ATTACK, StatType.SP_DEFENSE),
    BOLD("Bold", StatType.DEFENSE, StatType.ATTACK),
    DOCILE("Docile", null, null),
    RELAXED("Relaxed", StatType.DEFENSE, StatType.SPEED),
    IMPISH("Impish", StatType.DEFENSE, StatType.SP_ATTACK),
    LAX("Lax", StatType.DEFENSE, StatType.SP_DEFENSE),
    TIMID("Timid", StatType.SPEED, StatType.ATTACK),
    HASTY("Hasty", StatType.SPEED, StatType.DEFENSE),
    SERIOUS("Serious", null, null),
    JOLLY("Jolly", StatType.SPEED, StatType.SP_ATTACK),
    NAIVE("Naive", StatType.SPEED, StatType.SP_DEFENSE),
    MODEST("Modest", StatType.SP_ATTACK, StatType.ATTACK),
    MILD("Mild", StatType.SP_ATTACK, StatType.DEFENSE),
    QUIET("Quiet", StatType.SP_ATTACK, StatType.SPEED),
    BASHFUL("Bashful", null, null),
    RASH("Rash", StatType.SP_ATTACK, StatType.SP_DEFENSE),
    CALM("Calm", StatType.SP_DEFENSE, StatType.ATTACK),
    GENTLE("Gentle", StatType.SP_DEFENSE, StatType.DEFENSE),
    SASSY("Sassy", StatType.SP_DEFENSE, StatType.SPEED),
    CAREFUL("Careful", StatType.SP_DEFENSE, StatType.SP_ATTACK),
    QUIRKY("Quirky", null, null);

    /** Nature multiplier applied to [stat]: 1.1, 0.9 or 1.0. */
    fun multiplierFor(stat: StatType): Double = when (stat) {
        increased -> 1.1
        decreased -> 0.9
        else -> 1.0
    }
}
