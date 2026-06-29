package com.pokp.pokedex

import android.app.Application
import com.pokp.pokedex.di.AppContainer

class PokedexApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
