package com.pokp.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pokp.app.ui.DownloadScreen
import com.pokp.app.ui.theme.PokpTheme
import com.pokp.app.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val sharedUrl = extractSharedUrl(intent)

        setContent {
            val vm: DownloadViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
            PokpTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                DownloadScreen(viewModel = vm, sharedUrl = sharedUrl)
            }
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        return null
    }
}
