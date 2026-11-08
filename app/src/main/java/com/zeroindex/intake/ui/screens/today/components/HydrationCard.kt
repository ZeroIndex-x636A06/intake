package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.repository.HydrationDay
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.domain.units.VolumeUnit

/**
 * Amendment §3: water as a primary metric on Today. Progress toward the day's
 * target, the reason the target moved if it did, and one-tap presets - most
 * drinks are the same glass or bottle every time, so logging one should never
 * take more than a tap.
 */
@Composable
fun HydrationCard(
    day: HydrationDay,
    presets: List<HydrationPresetEntity>,
    unit: VolumeUnit,
    onPreset: (HydrationPresetEntity) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val consumed = unit.format(day.consumedMl)
    val target = unit.format(day.target.totalMl)
    val left = (day.target.totalMl - day.consumedMl).coerceAtLeast(0.0)

    IntakeCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Edit water presets and target", onClick = onOpen)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Water: $consumed of $target. " +
                        if (left > 0) "${unit.format(left)} to go." else "Target reached."
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Water")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$consumed of $target",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = if (left > 0) "${unit.format(left)} to go" else "Target reached",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(9.dp))
        ProgressBar(fraction = day.progress)

        if (day.target.reasons.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = day.target.reasons.joinToString("  ·  ") { "+${unit.format(it.ml)} ${it.label}" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            presets.forEach { preset ->
                PresetButton(
                    label = "+ ${preset.name}",
                    detail = unit.format(preset.volumeMl),
                    onClick = { onPreset(preset) },
                )
            }
            PresetButton(label = "Custom", detail = null, onClick = onOpen)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
        )
    }
}

@Composable
private fun PresetButton(label: String, detail: String?, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = IntakeShape.Chip,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
