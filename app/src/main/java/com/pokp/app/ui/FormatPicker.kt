package com.pokp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokp.app.domain.DownloadFormat

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FormatPicker(
    selected: DownloadFormat,
    onSelected: (DownloadFormat) -> Unit,
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

        if (!isAudioMode) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadFormat.videoOptions.forEach { fmt ->
                    FilterChip(
                        selected = selected == fmt,
                        onClick = { onSelected(fmt) },
                        label = { Text(fmt.label.removePrefix("MP4 ")) },
                    )
                }
            }
        }
    }
}
