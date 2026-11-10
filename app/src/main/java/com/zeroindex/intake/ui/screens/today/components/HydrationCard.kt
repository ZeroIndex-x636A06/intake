package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.repository.HydrationDay
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.motion.WaterWave
import com.zeroindex.intake.ui.theme.WaterAccent

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
    unit: VolumeUnit,
    onMinus: (HydrationLogEntity) -> Unit,
    onPlus: (HydrationLogEntity) -> Unit,
    onOpenDrinks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = drinkRows(day.logs)
    IntakeCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Water", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            val sub = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(unit.formatNumber(day.consumedMl)) }
                    withStyle(SpanStyle(fontSize = 13.sp, color = sub)) { append(" of ${unit.format(day.target.totalMl)}") }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        WaterWave(fraction = day.progress, water = WaterAccent, track = MaterialTheme.colorScheme.surfaceVariant, height = 14.dp)
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) {
            Text("Nothing logged yet today.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        }
        rows.forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.drink.label, style = MaterialTheme.typography.titleSmall)
                    Text("${unit.format(row.volumeMl)} × ${row.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onMinus(row.newest) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = "Remove one ${row.drink.label.lowercase()}")
                }
                IconButton(onClick = { onPlus(row.newest) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Add one more ${row.drink.label.lowercase()}")
                }
            }
            if (i != rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        TextButton(onClick = onOpenDrinks, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Log a different drink")
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
