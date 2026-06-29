package com.pokp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokp.app.data.InitState
import com.pokp.app.domain.DownloadFormat
import com.pokp.app.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    sharedUrl: String? = null,
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val initState by viewModel.initState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var urlText by remember { mutableStateOf(sharedUrl.orEmpty()) }
    var format by remember { mutableStateOf(DownloadFormat.VIDEO_720) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(sharedUrl) { if (!sharedUrl.isNullOrBlank()) urlText = sharedUrl }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pokp Downloader") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (initState is InitState.Initializing) {
                InitBanner("Preparando o motor de download (primeira execução pode demorar)…")
            }
            (initState as? InitState.Failed)?.let {
                InitBanner("Erro de inicialização: ${it.message}", isError = true)
            }
            if (!viewModel.spotifyConfigured) {
                InitBanner(
                    "Spotify não configurado — só links do YouTube funcionarão até adicionar as credenciais.",
                )
            }

            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Cole o link (YouTube ou Spotify)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FormatPicker(selected = format, onSelected = { format = it })

            Button(
                onClick = {
                    viewModel.enqueue(urlText, format)
                    urlText = ""
                },
                enabled = urlText.isNotBlank() && initState is InitState.Ready,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (initState is InitState.Ready) "Baixar" else "Aguarde…")
            }

            if (tasks.isEmpty()) {
                Text(
                    "Nenhum download ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        DownloadItemRow(
                            task = task,
                            onCancel = { viewModel.cancel(task) },
                            onRemove = { viewModel.remove(task) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InitBanner(text: String, isError: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
