package com.pokp.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pokp.app.ui.DownloadScreen
import com.pokp.app.ui.theme.PokpTheme
import com.pokp.app.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask for the notification permission on Android 13+ (used by future foreground service).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val initialShared = extractSharedUrl(intent)

        setContent {
            PokpTheme {
                var shared by remember { mutableStateOf(initialShared) }
                val vm: DownloadViewModel = viewModel()
                DownloadScreen(viewModel = vm, sharedUrl = shared)
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
