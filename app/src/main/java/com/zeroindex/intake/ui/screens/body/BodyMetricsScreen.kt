package com.zeroindex.intake.ui.screens.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.zeroindex.intake.ui.components.FormRow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BodyMetricsScreen(
    contentPadding: PaddingValues,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BodyMetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val weightUnit = state.settings.weightUnit
    val lengthUnit = state.settings.lengthUnit

    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var arm by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        Text(
            // SPEC §3.5: entered in the display unit, stored in kilograms.
            text = "Entered in ${weightUnit.label} and ${lengthUnit.label}; stored in " +
                "kilograms and centimetres so the unit setting can change later without " +
                "touching your history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        IntakeCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Num(weight, { weight = it }, "Weight (${weightUnit.label})", Modifier.fillMaxWidth())
                Num(bodyFat, { bodyFat = it }, "Body fat % (optional)", Modifier.fillMaxWidth())
            }
        }

        IntakeCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Measurements (optional)", style = MaterialTheme.typography.titleLarge)
                FormRow(
                    left = { m -> Num(waist, { waist = it }, "Waist (${lengthUnit.label})", m) },
                    right = { m -> Num(chest, { chest = it }, "Chest (${lengthUnit.label})", m) },
                )
                FormRow(
                    left = { m -> Num(arm, { arm = it }, "Arm (${lengthUnit.label})", m) },
                    right = { m -> Num(thigh, { thigh = it }, "Thigh (${lengthUnit.label})", m) },
                )
                Num(hip, { hip = it }, "Hip (${lengthUnit.label})", Modifier.fillMaxWidth())
            }
        }

        IntakeCard {
            LabeledField(label = "Notes", value = notes, onValueChange = { notes = it })
        }

        Button(
            onClick = {
                viewModel.save(
                    displayWeight = weight.toDoubleOrNull() ?: return@Button,
                    bodyFatPercent = bodyFat.toDoubleOrNull(),
                    displayWaist = waist.toDoubleOrNull(),
                    displayChest = chest.toDoubleOrNull(),
                    displayArm = arm.toDoubleOrNull(),
                    displayThigh = thigh.toDoubleOrNull(),
                    displayHip = hip.toDoubleOrNull(),
                    notes = notes.ifBlank { null },
                    onSaved = onSaved,
                )
            },
            enabled = weight.toDoubleOrNull()?.let { it > 0 } == true,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Log weight") }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Num(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    LabeledField(
        label = label,
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == '.' }) },
        keyboardType = KeyboardType.Decimal,
        modifier = modifier,
    )
}
