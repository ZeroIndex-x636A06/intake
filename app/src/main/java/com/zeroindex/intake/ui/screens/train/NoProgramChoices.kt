package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * The friendly empty state for a person with no program: three ways to get one, none of them
 * pre-made for you. [onFreestyle], when given, adds "just start lifting" underneath.
 */
@Composable
fun NoProgramChoices(
    onBuild: () -> Unit,
    onGenerate: () -> Unit,
    onTemplate: () -> Unit,
    modifier: Modifier = Modifier,
    onFreestyle: (() -> Unit)? = null,
) {
    IntakeCard(modifier = modifier) {
        Text(
            text = "No program yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "A program is your plan for the week: which exercises, how many sets and reps, and how the weight goes up. Start whichever way suits you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onGenerate,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Make one for me") }
            OutlinedButton(
                onClick = onBuild,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Build a program") }
            OutlinedButton(
                onClick = onTemplate,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Start from a template") }
            if (onFreestyle != null) {
                TextButton(onClick = onFreestyle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Or just start an empty workout")
                }
            }
        }
    }
}

/** A choice chip whose click handler comes last, so it can take a trailing lambda. */
@Composable
internal fun TrainChip(label: String, selected: Boolean, onClick: () -> Unit) {
    com.zeroindex.intake.ui.components.ChoiceChip(label = label, selected = selected, onClick = onClick)
}
