package com.pokp.pokedex.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.pokp.pokedex.PokedexApp

/** Retrieves the [PokedexApp] (and thus the DI container) from a ViewModel's creation extras. */
fun CreationExtras.pokedexApp(): PokedexApp =
    (this[APPLICATION_KEY] as PokedexApp)
