package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.zeroindex.intake.ui.motion.WaterWave
import com.zeroindex.intake.ui.motion.pressScale
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.zeroindex.intake.domain.hydration.WeatherAdjustment
import com.zeroindex.intake.domain.hydration.WeatherStatus
import kotlin.math.roundToInt
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
 * as drink chips (an icon, the amount, the name) so logging the usual glass or cup is one tap.
 * Tea and coffee count toward the day like water, so they sit beside it. The weather line only appears when
 * the user turned weather on, and says plainly when there is nothing to adjust.
 */
@OptIn(ExperimentalLayoutApi::class)
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

        weatherLine(day.weather)?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(11.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            presets.take(MAX_CHIPS).forEach { preset ->
                DrinkChip(
                    icon = preset.drinkType.icon(),
                    amount = "\uFF0B ${unit.formatNumber(preset.volumeMl)}",
                    name = preset.name.lowercase(),
                    description = "Add ${unit.format(preset.volumeMl)}, ${preset.name}",
                    onClick = { onPreset(preset) },
                )
            }
            DrinkChip(
                icon = Icons.Filled.Tune,
                amount = "\uFF0B Custom",
                name = "drink",
                description = "Add a custom drink",
                onClick = onOpen,
            )
        }
    }
}

private const val MAX_CHIPS = 6

/** The weather line is silent when the feature is off, and honest when it cannot help. */
private fun weatherLine(status: WeatherStatus): String? = when (status) {
    WeatherStatus.Off -> null
    WeatherStatus.NeedsLocation -> "Weather: no location set, so no weather adjustment."
    WeatherStatus.Unavailable -> "Weather: could not load today's forecast, so no weather adjustment."
    is WeatherStatus.Ready ->
        if (WeatherAdjustment.adjustmentMl(status.day) > 0) null
        else "Weather: ${status.day.tempMaxC.roundToInt()} \u00B0C today, no extra water needed."
}

@Composable
private fun WaterBar(fraction: Float) {
    // A sine-wave fill that rises on first show; the wave pauses off-screen and with reduce-motion.
    WaterWave(
        fraction = fraction,
        water = WaterAccent,
        track = MaterialTheme.colorScheme.surfaceVariant,
        height = 20.dp,
    )
}

@Composable
private fun DrinkChip(
    icon: ImageVector,
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
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = WaterAccent, modifier = Modifier.size(18.dp))
            Column {
                Text(amount, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
