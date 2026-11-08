package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.repository.HydrationDay
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.theme.WaterAccent

/**
 * Amendment §3: water as a primary metric on Today, laid out as mockup v2's
 * `.hydration` card. The amount is the loudest thing on the card, the meta
 * line says why the target moved, and the first three presets plus Custom sit
 * in four equal tiles so logging the usual glass is one tap.
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
    val left = (day.target.totalMl - day.consumedMl).coerceAtLeast(0.0)

    IntakeCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Edit water presets and target", onClick = onOpen)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Water today: ${unit.format(day.consumedMl)} of " +
                        "${unit.format(day.target.totalMl)}. " +
                        if (left > 0) "${unit.format(left)} to go." else "Target reached."
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Water today",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            val sub = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                buildAnnotatedString {
                    append(unit.formatNumber(day.consumedMl))
                    withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = sub)) {
                        append(" / ${unit.format(day.target.totalMl)}")
                    }
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(10.dp))
        WaterBar(day.progress)
        Spacer(Modifier.height(7.dp))

        Row(Modifier.fillMaxWidth()) {
            Text(
                if (day.target.reasons.isEmpty()) "Daily target" else "Dynamic target",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (day.target.reasons.isEmpty()) {
                    if (left > 0) "${unit.format(left)} to go" else "Target reached"
                } else {
                    day.target.reasons.joinToString(" · ") { "+${unit.format(it.ml)} · ${it.label}" }
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.take(3).forEach { preset ->
                PresetTile(
                    amount = "＋ ${unit.formatNumber(preset.volumeMl)}",
                    name = preset.name.lowercase(),
                    description = "Add ${unit.format(preset.volumeMl)}, ${preset.name}",
                    onClick = { onPreset(preset) },
                    modifier = Modifier.weight(1f),
                )
            }
            PresetTile(
                amount = "＋ Custom",
                name = "amount",
                description = "Add a custom amount",
                onClick = onOpen,
                modifier = Modifier.weight(1f),
            )
            repeat((3 - presets.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun WaterBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(WaterAccent, CircleShape),
        )
    }
}

@Composable
private fun PresetTile(
    amount: String,
    name: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(amount, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
