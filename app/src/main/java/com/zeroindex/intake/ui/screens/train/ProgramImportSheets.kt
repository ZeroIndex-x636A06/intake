package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.repository.ProgramImportPreview
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.screens.script.ScriptWarnings

/** What a program script will do, shown before anything is written. */
@Composable
fun ProgramImportPreviewDialog(
    preview: ProgramImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ready = preview as? ProgramImportPreview.Ready
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    ready == null -> "Can't import this file"
                    ready.batch.summary.updatingExisting -> "Update this program?"
                    else -> "Import this program?"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (preview) {
                    is ProgramImportPreview.Invalid -> Text(preview.message, style = MaterialTheme.typography.bodySmall)
                    is ProgramImportPreview.ScriptErrors -> Unit
                    is ProgramImportPreview.Ready -> {
                        val s = preview.batch.summary
                        Text(s.programName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${s.days.size} days, ${s.exerciseCount} exercises." +
                                if (s.updatingExisting) " This program is already in the app; importing updates it in place." else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        s.days.forEach { d ->
                            Text("• ${d.name}: ${d.exerciseCount} exercises", style = MaterialTheme.typography.bodySmall)
                        }
                        if (s.newExercises.isNotEmpty()) {
                            Text("New exercises (added to your library)", style = MaterialTheme.typography.titleSmall)
                            Text(s.newExercises.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                        if (s.matchedExercises.isNotEmpty()) {
                            Text("Matched to your library", style = MaterialTheme.typography.titleSmall)
                            Text(s.matchedExercises.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                        if (s.rules.isNotEmpty()) {
                            Text("Progression rules", style = MaterialTheme.typography.titleSmall)
                            s.rules.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (s.removedExercises > 0 || s.removedDays > 0) {
                            SandNote(
                                "${s.removedExercises} exercises and ${s.removedDays} days from the earlier version are not in this file. " +
                                    "They are hidden, not erased; your logged sets are kept.",
                            )
                        }
                        s.notices.forEach { SandNote(it) }
                        ScriptWarnings(preview.warnings)
                    }
                }
            }
        },
        confirmButton = {
            if (ready != null) {
                TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text("Import") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (ready != null) "Cancel" else "Close")
            }
        },
    )
}
