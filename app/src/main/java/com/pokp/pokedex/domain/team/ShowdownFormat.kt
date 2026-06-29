package com.pokp.pokedex.domain.team

import com.pokp.pokedex.domain.StatType

/**
 * Serializes and parses teams in the Pokémon Showdown text format, e.g.:
 *
 * ```
 * Pikachu (M) @ Light Ball
 * Ability: Static
 * Level: 50
 * EVs: 252 SpA / 4 SpD / 252 Spe
 * Timid Nature
 * - Thunderbolt
 * - Volt Switch
 * - Surf
 * - Hidden Power Ice
 * ```
 *
 * Species ids are not resolved here; [parse] returns members with `speciesId = 0` and the
 * raw species name, leaving id resolution to the data layer.
 */
object ShowdownFormat {

    private val statToken = linkedMapOf(
        StatType.HP to "HP",
        StatType.ATTACK to "Atk",
        StatType.DEFENSE to "Def",
        StatType.SP_ATTACK to "SpA",
        StatType.SP_DEFENSE to "SpD",
        StatType.SPEED to "Spe",
    )
    private val tokenToStat = statToken.entries.associate { (k, v) -> v to k }

    // ---------------- Export ----------------

    fun export(members: List<TeamMember>): String =
        members.joinToString("\n\n") { exportMember(it) } + "\n"

    private fun exportMember(m: TeamMember): String = buildString {
        // Header line: [Nickname (Species)] [(Gender)] [@ Item]
        val showNickname = !m.nickname.isNullOrBlank() && m.nickname != m.speciesName
        append(if (showNickname) "${m.nickname} (${m.speciesName})" else m.speciesName)
        m.gender?.takeIf { it == "M" || it == "F" }?.let { append(" ($it)") }
        m.item?.takeIf { it.isNotBlank() }?.let { append(" @ $it") }
        append("\n")

        m.ability?.takeIf { it.isNotBlank() }?.let { append("Ability: $it\n") }
        if (m.level != 100) append("Level: ${m.level}\n")
        if (m.shiny) append("Shiny: Yes\n")
        m.teraType?.takeIf { it.isNotBlank() }?.let { append("Tera Type: $it\n") }
        if (!m.evs.isAllZero()) append("EVs: ${spreadString(m.evs)}\n")
        append("${m.nature} Nature\n")
        val ivString = spreadString(m.ivs, skipValue = 31)
        if (ivString.isNotEmpty()) append("IVs: $ivString\n")
        m.moves.filter { it.isNotBlank() }.forEach { append("- $it\n") }
    }.trimEnd('\n')

    private fun spreadString(spread: StatSpread, skipValue: Int = 0): String =
        statToken.entries
            .mapNotNull { (stat, token) ->
                val value = spread[stat]
                if (value == skipValue) null else "$value $token"
            }
            .joinToString(" / ")

    // ---------------- Parse ----------------

    /** Parses Showdown text into members (speciesId unresolved). Invalid blocks are skipped. */
    fun parse(text: String): List<TeamMember> {
        val blocks = text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
        return blocks.mapNotNull { parseBlock(it) }
    }

    private fun parseBlock(block: String): TeamMember? {
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        var header = lines.first()
        var item: String? = null
        var gender: String? = null
        var nickname: String? = null

        val atIndex = header.lastIndexOf(" @ ")
        if (atIndex >= 0) {
            item = header.substring(atIndex + 3).trim()
            header = header.substring(0, atIndex).trim()
        }

        Regex("\\(([MFN])\\)$").find(header)?.let { match ->
            gender = match.groupValues[1]
            header = header.removeRange(match.range).trim()
        }

        val species: String
        val nameMatch = Regex("^(.*) \\((.+)\\)$").find(header)
        if (nameMatch != null) {
            nickname = nameMatch.groupValues[1].trim()
            species = nameMatch.groupValues[2].trim()
        } else {
            species = header.trim()
        }
        if (species.isEmpty()) return null

        var ability: String? = null
        var level = 100
        var shiny = false
        var tera: String? = null
        var nature = "Hardy"
        var evs = StatSpread.ZERO
        var ivs = StatSpread.MAX_IVS
        val moves = mutableListOf<String>()

        lines.drop(1).forEach { line ->
            when {
                line.startsWith("Ability:") -> ability = line.substringAfter(":").trim()
                line.startsWith("Level:") -> level = line.substringAfter(":").trim().toIntOrNull() ?: 100
                line.startsWith("Shiny:") -> shiny = line.substringAfter(":").trim().equals("yes", true)
                line.startsWith("Tera Type:") -> tera = line.substringAfter(":").trim()
                line.startsWith("EVs:") -> evs = parseSpread(line.substringAfter(":"), 0)
                line.startsWith("IVs:") -> ivs = parseSpread(line.substringAfter(":"), 31)
                line.startsWith("-") -> line.removePrefix("-").trim().takeIf { it.isNotEmpty() }?.let { moves += it }
                line.endsWith(" Nature") -> nature = line.removeSuffix(" Nature").trim()
                else -> Unit
            }
        }

        return TeamMember(
            speciesId = 0,
            speciesName = species,
            nickname = nickname,
            item = item,
            ability = ability,
            level = level,
            nature = nature,
            teraType = tera,
            gender = gender,
            shiny = shiny,
            evs = evs,
            ivs = ivs,
            moves = moves,
        )
    }

    private fun parseSpread(raw: String, base: Int): StatSpread {
        var spread = StatSpread(base, base, base, base, base, base)
        raw.split("/").forEach { part ->
            val tokens = part.trim().split(Regex("\\s+"))
            if (tokens.size >= 2) {
                val value = tokens[0].toIntOrNull()
                val stat = tokenToStat[tokens[1]]
                if (value != null && stat != null) {
                    spread = spread.withStat(stat, value)
                }
            }
        }
        return spread
    }

    private fun StatSpread.withStat(stat: StatType, value: Int): StatSpread = when (stat) {
        StatType.HP -> copy(hp = value)
        StatType.ATTACK -> copy(atk = value)
        StatType.DEFENSE -> copy(def = value)
        StatType.SP_ATTACK -> copy(spa = value)
        StatType.SP_DEFENSE -> copy(spd = value)
        StatType.SPEED -> copy(spe = value)
    }
}
