package com.pokp.app

import android.app.Application
import com.pokp.app.data.InitManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Kick off the (slow) native init early so the UI is usable as soon as possible.
        appScope.launch { InitManager.ensureInitialized(this@MainApplication) }
    }
}
