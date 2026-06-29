package com.pokp.pokedex.ui.navigation

object Routes {
    const val LIST = "list"
    const val SETTINGS = "settings"
    const val ARG_ID = "id"
    const val DETAIL = "detail/{$ARG_ID}"

    const val TEAMS = "teams"
    const val ARG_TEAM_ID = "teamId"
    const val TEAM_EDITOR = "team_editor/{$ARG_TEAM_ID}"

    fun detail(id: Int): String = "detail/$id"
    fun teamEditor(id: Long): String = "team_editor/$id"
}
