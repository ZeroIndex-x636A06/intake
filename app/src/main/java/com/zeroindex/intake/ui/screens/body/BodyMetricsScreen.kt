package com.zeroindex.intake.ui.screens.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.zeroindex.intake.ui.components.ScreenHeading

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeading("Log weight")
        Text(
            // SPEC §3.5: entered in the display unit, stored in kilograms.
            text = "Entered in ${weightUnit.label} and ${lengthUnit.label}; stored in " +
                "kilograms and centimetres so the unit setting can change later without " +
                "touching your history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Num(weight, { weight = it }, "Weight (${weightUnit.label})", Modifier.fillMaxWidth())
        Num(bodyFat, { bodyFat = it }, "Body fat % (optional)", Modifier.fillMaxWidth())

        Text(
            text = "Measurements (optional)",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Num(waist, { waist = it }, "Waist (${lengthUnit.label})", Modifier.weight(1f))
            Num(chest, { chest = it }, "Chest (${lengthUnit.label})", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Num(arm, { arm = it }, "Arm (${lengthUnit.label})", Modifier.weight(1f))
            Num(thigh, { thigh = it }, "Thigh (${lengthUnit.label})", Modifier.weight(1f))
        }
        Num(hip, { hip = it }, "Hip (${lengthUnit.label})", Modifier.fillMaxWidth())

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
        )

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
            modifier = Modifier.fillMaxWidth().height(52.dp),
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
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
