package com.pokp.pokedex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pokp.pokedex.ui.detail.PokemonDetailScreen
import com.pokp.pokedex.ui.list.PokemonListScreen
import com.pokp.pokedex.ui.settings.SettingsScreen

@Composable
fun PokedexNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            PokemonListScreen(
                onPokemonClick = { id -> navController.navigate(Routes.detail(id)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.IntType }),
        ) {
            PokemonDetailScreen(
                onPokemonClick = { id -> navController.navigate(Routes.detail(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
