package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.seed.ProgramTemplate
import com.zeroindex.intake.data.seed.ProgramTemplates
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * Starting points. Picking one makes your own copy, which you can edit freely; the template itself
 * is never changed and is never a program until you copy it.
 */
@Composable
fun ProgramTemplatesScreen(
    contentPadding: PaddingValues,
    onCreated: (programId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "heading") {
            Text("Pick one to make your own copy. Change anything afterwards.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(items = ProgramTemplates.ALL, key = { it.id }) { template ->
            TemplateCard(template, busy = state.isWorking, onUse = { viewModel.createFromTemplate(template.id, onCreated) })
        }
    }
}

@Composable
private fun TemplateCard(template: ProgramTemplate, busy: Boolean, onUse: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IntakeCard {
        Eyebrow("${template.dayCount} days · ${template.level}")
        Spacer(Modifier.height(4.dp))
        Text(template.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(template.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (open) {
            Spacer(Modifier.height(8.dp))
            template.blueprint.days.forEach { day ->
                Text(day.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    day.exercises.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onUse,
            enabled = !busy,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Use this template") }
        OutlinedButton(
            onClick = { open = !open },
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(if (open) "Hide the days" else "See the days") }
    }
}
