package com.zeroindex.intake.ui.screens.script

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.script.Diagnostic
import com.zeroindex.intake.domain.script.ScriptInput
import com.zeroindex.intake.domain.script.Severity
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.SandNote

/** Mime types the import picker offers: some file managers report .md as octet-stream. */
val ScriptMimeTypes = arrayOf("text/*", "application/json", "application/octet-stream")

/** Writes [text] to a document the user chose with CreateDocument. True when it was written. */
fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
}.getOrDefault(false)

/** "page" and "anchor" of a diagnostic's wiki link, e.g. "program#sets". */
fun wikiTarget(d: Diagnostic): Pair<String, String?> {
    val parts = d.anchor.split('#', limit = 2)
    return parts[0] to parts.getOrNull(1)
}

/**
 * Every problem in a script that cannot be imported, each as "Line 14, column 3: message" with a
 * "How to fix" button that opens the reference at the rule. Nothing has been imported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptDiagnosticsSheet(
    diagnostics: List<Diagnostic>,
    onHowToFix: (page: String, anchor: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val errors = diagnostics.count { it.severity == Severity.ERROR }
            Eyebrow("Nothing was imported")
            Text(
                "This file has $errors problem${if (errors == 1) "" else "s"}",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Fix these in the file and import it again. Nothing has been changed in the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.weightedHeight()) {
                items(diagnostics, key = { "${it.line}:${it.column}:${it.code}:${it.message.hashCode()}" }) { d ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (d.severity == Severity.ERROR) "Error" else "Warning",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (d.severity == Severity.ERROR) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(ScriptInput.describe(d), style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(
                                onClick = { wikiTarget(d).let { (p, a) -> onHowToFix(p, a) } },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("How to fix") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
        }
    }
}

private fun Modifier.weightedHeight(): Modifier = this.heightIn(max = 420.dp)

/** Script warnings for the preview, listed next to the allergen hits. They never block the import. */
@Composable
fun ScriptWarnings(warnings: List<Diagnostic>) {
    if (warnings.isEmpty()) return
    SandNote(
        title = "${warnings.size} warning${if (warnings.size == 1) "" else "s"} in the file",
        body = warnings.take(12).joinToString("\n") { "• ${ScriptInput.describe(it)}" } +
            if (warnings.size > 12) "\n…and ${warnings.size - 12} more" else "",
    )
}
