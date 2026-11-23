package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.zeroindex.intake.ui.components.LineButton
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.SpaceGrotesk
import com.zeroindex.intake.ui.theme.intake
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.repository.HydrationDay
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.motion.WaterWave

/** One drink and size with how many were logged today ("Water 250 ml x3"); [newest] is the entry a minus removes. */
data class DrinkRow(val drink: DrinkType, val volumeMl: Double, val count: Int, val newest: HydrationLogEntity)

/** Groups today's entries by drink and size, in the order the drinks were first logged. */
fun drinkRows(logs: List<HydrationLogEntity>): List<DrinkRow> =
    logs.sortedBy { it.loggedAt }.groupBy { it.drinkType to it.volumeMl }.map { (key, list) ->
        DrinkRow(key.first, key.second, list.size, list.maxBy { it.loggedAt })
    }

/**
 * Option A's water card: today's total against the target, and one row per drink already logged today with a minus and
 * a plus. That is all it does (minus removes the newest of that drink, plus adds one more of the same drink and size).
 * A new drink is logged on the Drinks page ("Log a different drink"). No setting is here: the target, units and quick
 * sizes are in Settings > Water and drinks.
 */
@Composable
fun HydrationCard(
    day: HydrationDay,
    isToday: Boolean,
    unit: VolumeUnit,
    onMinus: (HydrationLogEntity) -> Unit,
    onPlus: (HydrationLogEntity) -> Unit,
    onOpenDrinks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = drinkRows(day.logs)
    IntakeCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.intake.water, contentColor = MaterialTheme.colorScheme.surface, shape = IntakeShape.IconSmall, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(IntakeIcons.Drop, contentDescription = null, modifier = Modifier.size(IconSize.Small)) }
            }
            Spacer(Modifier.width(10.dp))
            Text("Water", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val sub = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)) { append(unit.formatNumber(day.consumedMl)) }
                    withStyle(SpanStyle(fontSize = 13.sp, color = sub)) { append(" of ${unit.format(day.target.totalMl)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        WaterWave(fraction = day.progress, water = MaterialTheme.intake.water, track = MaterialTheme.intake.soft, height = 14.dp)
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text(if (isToday) "Nothing logged yet today." else "Nothing logged that day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${row.drink.label} ${unit.format(row.volumeMl)} × ${row.count}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StepperButton(IntakeIcons.Minus, "Remove one ${row.drink.label.lowercase()}") { onMinus(row.newest) }
                Spacer(Modifier.width(8.dp))
                StepperButton(IntakeIcons.Plus, "Add one more ${row.drink.label.lowercase()}") { onPlus(row.newest) }
            }
        }
        Spacer(Modifier.height(4.dp))
        LineButton(onClick = onOpenDrinks, small = true) { Text("Log a different drink") }
    }
}

/** A 44 dp outlined circle (1.5 dp `line`), the stepper button of a water row. */
@Composable
private fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(IntakeSpacing.TouchTarget).border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(IconSize.Small)) }
}
