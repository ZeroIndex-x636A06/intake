package com.zeroindex.intake.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.settings.HydrationSettings
import com.zeroindex.intake.domain.hydration.DrinkOverride
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.hydration.Drinks
import com.zeroindex.intake.domain.hydration.SweatMeasurement
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.SettingRow

/** Typed text for the Advanced section; blank means "use the default". Applied by the sheet's Save button. */
@Stable
class AdvancedEdits(settings: HydrationSettings, weightUnit: WeightUnit, volumeUnit: VolumeUnit) {
    val factor = mutableStateMapOf<DrinkType, String>()
    val caffeine = mutableStateMapOf<DrinkType, String>()
    val abv = mutableStateMapOf<DrinkType, String>()
    var pre by mutableStateOf("")
    var post by mutableStateOf("")
    var fluid by mutableStateOf("")
    var minutes by mutableStateOf("")
    var useMeasured by mutableStateOf(settings.useMeasuredSweat)

    init {
        settings.drinkOverrides.forEach { (type, o) ->
            o.factor?.let { factor[type] = number(it) }
            o.caffeineMgPer250?.let { caffeine[type] = number(it) }
            o.abvPercent?.let { abv[type] = number(it) }
        }
        settings.sweat?.let {
            pre = number(UnitConverter.round(weightUnit.fromKg(it.preKg), 1))
            post = number(UnitConverter.round(weightUnit.fromKg(it.postKg), 1))
            fluid = number(UnitConverter.round(volumeUnit.fromMl(it.fluidMl), 1))
            minutes = number(it.minutes)
        }
    }

    fun overrideFor(type: DrinkType) = DrinkOverride(
        factor = factor[type]?.toDoubleOrNull(),
        caffeineMgPer250 = caffeine[type]?.toDoubleOrNull(),
        abvPercent = abv[type]?.toDoubleOrNull(),
    )

    fun measurement(weightUnit: WeightUnit, volumeUnit: VolumeUnit): SweatMeasurement? {
        val a = pre.toDoubleOrNull() ?: return null
        val b = post.toDoubleOrNull() ?: return null
        val m = minutes.toDoubleOrNull() ?: return null
        val f = fluid.toDoubleOrNull() ?: 0.0
        return SweatMeasurement(weightUnit.toKg(a), weightUnit.toKg(b), volumeUnit.toMl(f), m)
    }

    fun resetDrinks() {
        factor.clear()
        caffeine.clear()
        abv.clear()
    }

    private fun number(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}

private fun numeric(text: String, negative: Boolean = false) =
    text.filter { it.isDigit() || it == '.' || (negative && it == '-') }

/**
 * Advanced: the exact value of every drink, and the user's own sweat rate. Nothing here is required; the
 * defaults are sensible and their sources are in docs/HYDRATION.md.
 */
@Composable
fun HydrationAdvanced(
    edits: AdvancedEdits,
    weightUnit: WeightUnit,
    volumeUnit: VolumeUnit,
) {
    Column {
        Eyebrow("Drink values")
        Text(
            "Leave a box empty to use the default shown in it. Adds to hydration is a multiplier: 1 counts the full " +
                "amount, 1.5 counts half as much again, and a negative number takes water away. Caffeine is per 250 ml. " +
                "Strength is alcohol by volume.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        DrinkType.entries.forEach { type ->
            val abvNow = edits.abv[type]?.toDoubleOrNull() ?: type.abvPercent
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(type.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (type == DrinkType.TEA || type == DrinkType.COFFEE) {
                    InfoTip("Counts in full toward your water. At normal amounts they hydrate about as well as water does.")
                }
                if (type == DrinkType.SPIRITS) {
                    InfoTip("Counts as minus the pure alcohol in the glass: a 44 ml shot at 40% takes away about 18 ml. The default follows the strength.")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(
                    label = "Adds to hydration",
                    value = edits.factor[type].orEmpty(),
                    onValueChange = { edits.factor[type] = numeric(it, negative = true).take(6) },
                    placeholder = "%.2f".format(Drinks.defaultFactor(type, abvNow)),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                LabeledField(
                    label = "Caffeine mg",
                    value = edits.caffeine[type].orEmpty(),
                    onValueChange = { edits.caffeine[type] = numeric(it).take(5) },
                    placeholder = type.caffeineMgPer250.toInt().toString(),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                LabeledField(
                    label = "Strength %",
                    value = edits.abv[type].orEmpty(),
                    onValueChange = { edits.abv[type] = numeric(it).take(5) },
                    placeholder = type.abvPercent.toInt().toString(),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        TextButton(onClick = edits::resetDrinks) { Text("Reset drink values to defaults") }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Measure my sweat rate", modifier = Modifier.weight(1f))
            InfoTip(
                "Weigh yourself before and after a session, with the same clothes and after drying off. Each kilogram lost is about " +
                    "a litre of fluid. Add what you drank during it, divide by the hours, and that is your hourly sweat rate.",
            )
        }
        Text(
            "Weigh yourself before and after one session, note what you drank during it, and the app works out your own " +
                "litres per hour to use instead of the defaults.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField(
                label = "Weight before (${weightUnit.label})",
                value = edits.pre,
                onValueChange = { edits.pre = numeric(it).take(6) },
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                label = "Weight after (${weightUnit.label})",
                value = edits.post,
                onValueChange = { edits.post = numeric(it).take(6) },
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            LabeledField(
                label = "Drunk during (${volumeUnit.label})",
                value = edits.fluid,
                onValueChange = { edits.fluid = numeric(it).take(6) },
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                label = "Duration (min)",
                value = edits.minutes,
                onValueChange = { edits.minutes = numeric(it).take(4) },
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        val m = edits.measurement(weightUnit, volumeUnit)
        val working = m?.calculation(
            weightLabel = { kg -> "%.1f %s".format(weightUnit.fromKg(kg), weightUnit.label) },
            litreLabel = { l -> "%.2f L".format(l) },
        )
        if (working != null) {
            Text(
                working,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (m != null) {
            Text(
                "Those numbers do not give a believable rate (0.1 to 3 L/h, over at least 10 minutes). Check them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SettingRow(
            title = "Use my measured rate",
            subtitle = "Replaces the light, moderate and hard defaults when a workout adds sweat water",
            showDivider = false,
        ) {
            Switch(checked = edits.useMeasured && working != null, onCheckedChange = { edits.useMeasured = it }, enabled = working != null)
        }
    }
}
