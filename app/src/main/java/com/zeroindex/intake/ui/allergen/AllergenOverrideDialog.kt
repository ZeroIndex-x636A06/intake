package com.zeroindex.intake.ui.allergen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.allergen.OverridePrompt

/**
 * The warning before "Choose anyway" on an option the local engine says contains an allergen the person avoids. It names
 * the allergens and the evidence, says plainly that the warning stays on the entry, and puts focus on Cancel: the safe
 * choice is the default, the red button is deliberate.
 */
@Composable
fun AllergenOverrideDialog(prompt: OverridePrompt, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(prompt.optionKey) { runCatching { cancelFocus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Log despite your allergen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(prompt.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(prompt.headline, color = AllergenColors.red, fontWeight = FontWeight.Bold)
                if (prompt.evidence.isNotEmpty()) {
                    Text("Why: " + prompt.evidence.joinToString("; "), style = MaterialTheme.typography.bodyMedium)
                }
                Text(prompt.consequence, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = AllergenColors.red, contentColor = AllergenColors.onSolid)) {
                Text("Choose anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) { Text("Cancel") }
        },
    )
}
