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
import com.pokp.pokedex.ui.teams.TeamEditorScreen
import com.pokp.pokedex.ui.teams.TeamsListScreen

@Composable
fun PokedexNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            PokemonListScreen(
                onPokemonClick = { id -> navController.navigate(Routes.detail(id)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onTeamsClick = { navController.navigate(Routes.TEAMS) },
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
        composable(Routes.TEAMS) {
            TeamsListScreen(
                onBack = { navController.popBackStack() },
                onOpenTeam = { id -> navController.navigate(Routes.teamEditor(id)) },
            )
        }
        composable(
            route = Routes.TEAM_EDITOR,
            arguments = listOf(navArgument(Routes.ARG_TEAM_ID) { type = NavType.LongType }),
        ) {
            TeamEditorScreen(
                onBack = { navController.popBackStack() },
                onPokemonClick = { id -> navController.navigate(Routes.detail(id)) },
            )
        }
    }
}
