package com.pokp.pokedex.ui.navigation

object Routes {
    const val LIST = "list"
    const val SETTINGS = "settings"
    const val ARG_ID = "id"
    const val DETAIL = "detail/{$ARG_ID}"

    fun detail(id: Int): String = "detail/$id"
}
