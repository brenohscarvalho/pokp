package com.pokp.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pokp.app.data.InitState
import com.pokp.app.domain.DownloadFormat
import com.pokp.app.viewmodel.DownloadViewModel
import com.pokp.app.viewmodel.EnqueueOptions

private enum class Screen { MAIN, SETTINGS, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(viewModel: DownloadViewModel, sharedUrl: String? = null) {
    var screen by remember { mutableStateOf(Screen.MAIN) }
    when (screen) {
        Screen.SETTINGS -> SettingsScreen(viewModel) { screen = Screen.MAIN }
        Screen.HISTORY -> HistoryScreen(viewModel) { screen = Screen.MAIN }
        Screen.MAIN -> MainScreen(
            viewModel = viewModel,
            sharedUrl = sharedUrl,
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenHistory = { screen = Screen.HISTORY },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    viewModel: DownloadViewModel,
    sharedUrl: String?,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val initState by viewModel.initState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val previewLoading by viewModel.previewLoading.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val defaultBitrate by viewModel.bitrate.collectAsStateWithLifecycle()
    val defaultSubs by viewModel.subtitles.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    var urlText by remember { mutableStateOf(sharedUrl.orEmpty()) }
    var format by remember { mutableStateOf(DownloadFormat.VIDEO_720) }
    var bitrate by remember { mutableStateOf(defaultBitrate) }
    var subtitles by remember { mutableStateOf(defaultSubs) }
    var trimStart by remember { mutableStateOf("") }
    var trimEnd by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var wholePlaylist by remember { mutableStateOf(false) }

    LaunchedEffect(sharedUrl) { if (!sharedUrl.isNullOrBlank()) urlText = sharedUrl }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    fun currentOptions() = EnqueueOptions(
        format = format,
        bitrate = bitrate,
        subtitles = subtitles,
        trimStart = trimStart.ifBlank { null },
        trimEnd = trimEnd.ifBlank { null },
        wholePlaylist = wholePlaylist,
    )

    fun submit() {
        viewModel.enqueue(urlText, currentOptions())
        urlText = ""
        wholePlaylist = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pokp Downloader") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "Histórico")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            update?.let { info ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Nova versão ${info.version} disponível",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = {
                            openUrl(context, info.apkUrl ?: info.pageUrl)
                            viewModel.dismissUpdate()
                        }) { Text("Baixar") }
                        TextButton(onClick = { viewModel.dismissUpdate() }) { Text("X") }
                    }
                }
            }

            if (initState is InitState.Initializing) {
                Banner("Preparando o motor de download (primeira execução pode demorar)…")
            }
            (initState as? InitState.Failed)?.let {
                Banner("Erro de inicialização: ${it.message}", isError = true)
            }
            if (!viewModel.spotifyConfigured) {
                Banner("Spotify não configurado — só links do YouTube funcionam até adicionar as credenciais.")
            }

            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Cole o link (YouTube ou Spotify)") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let { urlText = it }
                    }) { Icon(Icons.Filled.ContentPaste, contentDescription = "Colar") }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            FormatPicker(
                selected = format,
                onSelected = { format = it },
                bitrate = bitrate,
                onBitrate = { bitrate = it },
                subtitles = subtitles,
                onSubtitles = { subtitles = it },
            )

            if (viewModel.isYoutubePlaylist(urlText)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = wholePlaylist, onCheckedChange = { wholePlaylist = it })
                    Text("Baixar a playlist inteira do YouTube")
                }
            }

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Ocultar opções avançadas" else "Opções avançadas (cortar trecho)")
            }
            if (showAdvanced) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = trimStart,
                        onValueChange = { trimStart = it },
                        label = { Text("Início (mm:ss)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = trimEnd,
                        onValueChange = { trimEnd = it },
                        label = { Text("Fim (mm:ss)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val ready = initState is InitState.Ready
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.preview(urlText) },
                    enabled = urlText.isNotBlank() && ready && !previewLoading,
                    modifier = Modifier.weight(1f),
                ) { Text(if (previewLoading) "..." else "Pré-visualizar") }
                Button(
                    onClick = { submit() },
                    enabled = urlText.isNotBlank() && ready,
                    modifier = Modifier.weight(1f),
                ) { Text(if (ready) "Baixar" else "Aguarde…") }
            }

            if (tasks.any { it.status.isFinished }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { viewModel.clearFinished() }) {
                        Text("Limpar concluídos")
                    }
                }
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
                            onRetry = { viewModel.retry(task) },
                            onOpen = { task.savedUri?.let { openUri(context, it) } },
                        )
                    }
                }
            }
        }
    }

    preview?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPreview() },
            confirmButton = {
                TextButton(onClick = {
                    submit()
                    viewModel.dismissPreview()
                }) { Text("Baixar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPreview() }) { Text("Fechar") }
            },
            title = { Text(info.title, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!info.thumbnail.isNullOrBlank()) {
                        AsyncImage(
                            model = info.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Text(info.subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            },
        )
    }
}

@Composable
private fun Banner(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun openUri(context: android.content.Context, uri: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
