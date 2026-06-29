package com.pokp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokp.app.domain.DownloadStatus
import com.pokp.app.domain.DownloadTask

@Composable
fun DownloadItemRow(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title.ifBlank { task.sourceUrl },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${task.format.label} · ${statusLabel(task)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val active = task.status == DownloadStatus.DOWNLOADING ||
                    task.status == DownloadStatus.RESOLVING ||
                    task.status == DownloadStatus.SAVING
                if (active) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Cancelar")
                    }
                } else {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Close, contentDescription = "Remover")
                    }
                }
            }

            if (task.status == DownloadStatus.DOWNLOADING) {
                if (task.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            task.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun statusLabel(task: DownloadTask): String = when (task.status) {
    DownloadStatus.QUEUED -> "Na fila"
    DownloadStatus.RESOLVING -> "Obtendo informações…"
    DownloadStatus.DOWNLOADING ->
        if (task.progress > 0f) "Baixando ${task.progress.toInt()}%" else "Baixando…"
    DownloadStatus.SAVING -> "Salvando…"
    DownloadStatus.DONE -> "Concluído ✓"
    DownloadStatus.FAILED -> "Falhou"
    DownloadStatus.CANCELLED -> "Cancelado"
}
