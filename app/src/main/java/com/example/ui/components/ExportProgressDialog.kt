package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

enum class ExportAudioChoice(val label: String) {
    VOCALS_ONLY("Isolated Vocals Video"),
    KARAOKE_ONLY("Karaoke Accompaniment Video"),
    ORIGINAL("Preserve Original Audio")
}

@Composable
fun ExportProgressDialog(
    isExporting: Boolean,
    progress: Float,
    onStartExport: (ExportAudioChoice) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedChoice by remember { mutableStateOf(ExportAudioChoice.VOCALS_ONLY) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = { Text(if (isExporting) "Exporting Video…" else "Export Mixed Video") },
        text = {
            Column {
                if (isExporting) {
                    Text(
                        text = "Muxing video with selected audio track…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Text(
                        text = "Choose which audio track to burn into the newly exported MP4 video:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ExportAudioChoice.entries.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedChoice == choice),
                                onClick = { selectedChoice = choice },
                                modifier = Modifier.testTag("export_radio_${choice.name}")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = choice.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isExporting) {
                Button(
                    onClick = { onStartExport(selectedChoice) },
                    modifier = Modifier.testTag("confirm_export_btn")
                ) {
                    Text("Export")
                }
            }
        },
        dismissButton = {
            if (!isExporting) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
