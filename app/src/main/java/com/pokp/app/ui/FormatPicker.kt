package com.pokp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokp.app.domain.AudioBitrate
import com.pokp.app.domain.DownloadFormat

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FormatPicker(
    selected: DownloadFormat,
    onSelected: (DownloadFormat) -> Unit,
    bitrate: AudioBitrate,
    onBitrate: (AudioBitrate) -> Unit,
    subtitles: Boolean,
    onSubtitles: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAudioMode = selected.isAudio
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !isAudioMode,
                onClick = { onSelected(DownloadFormat.VIDEO_720) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Vídeo (MP4)") }
            SegmentedButton(
                selected = isAudioMode,
                onClick = { onSelected(DownloadFormat.AUDIO_MP3) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Música (MP3)") }
        }

        if (isAudioMode) {
            Text("Qualidade do MP3", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioBitrate.all.forEach { br ->
                    FilterChip(
                        selected = bitrate == br,
                        onClick = { onBitrate(br) },
                        label = { Text(br.label) },
                    )
                }
            }
        } else {
            Text("Qualidade do vídeo", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadFormat.videoOptions.forEach { fmt ->
                    FilterChip(
                        selected = selected == fmt,
                        onClick = { onSelected(fmt) },
                        label = { Text(fmt.label.removePrefix("MP4 ")) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Baixar legendas", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = subtitles, onCheckedChange = onSubtitles)
            }
        }
    }
}
