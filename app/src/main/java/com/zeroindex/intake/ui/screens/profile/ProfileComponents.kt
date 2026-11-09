@file:OptIn(ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * A big, readable choice with a title and a line of explanation, used for the build picker and
 * the phase list. Selection is shown by border, fill AND a "Selected" word in the semantics, so
 * colour is never the only signal.
 */
@Composable
fun OptionTile(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (selected) scheme.primaryContainer else scheme.surface,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        shape = IntakeShape.InnerCard,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClickLabel = title, onClick = onClick),
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (secondary != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A wrapping row of single-choice chips. */
@Composable
fun <T> ChipGroup(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ChoiceChip(label = label(option), selected = option == selected, onClick = { onSelect(option) })
        }
    }
}

/** A multi-choice chip row (days of the week, equipment). */
@Composable
fun <T> MultiChipGroup(
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ChoiceChip(label = label(option), selected = option in selected, onClick = { onToggle(option) })
        }
    }
}

/**
 * A text field that saves as the person types, but never fights them: the text is only replaced
 * from outside when the saved value really differs from what is typed ("70." stays "70.").
 */
@Composable
fun CommitField(
    label: String,
    saved: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    placeholder: String? = null,
    supporting: String? = null,
) {
    var text by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) {
        if (text.toDoubleOrNull() != saved.toDoubleOrNull() || (text.isBlank() != saved.isBlank())) text = saved
    }
    LabeledField(
        label = label,
        value = text,
        onValueChange = {
            text = it
            onCommit(it)
        },
        modifier = modifier,
        placeholder = placeholder,
        keyboardType = keyboardType,
        supporting = supporting,
    )
}

/** A small heading with an InfoTip beside it. */
@Composable
fun TermHeading(title: String, tip: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
        InfoTip(text = tip, label = title)
    }
}

/** The plain-language glossary for the goal screens (TDEE and BMR never appear on their own). */
object GoalGlossary {
    const val DAILY_BURN =
        "Daily calorie burn (TDEE): what your body uses in a normal day, including walking about and training. " +
            "Eat this much and your weight stays put."
    const val CALORIES_AT_REST =
        "Calories at rest (BMR): what your body uses just to stay alive if you lay still all day. " +
            "It comes from your weight, height, age and sex."
    const val BUILD =
        "Build is only a starting point for wording and our first suggestion. It does not change your " +
            "numbers. The old ectomorph, mesomorph and endomorph labels are shown because people look for them."
    const val PROTEIN_PER_KG =
        "Grams of protein for each kilogram of body weight (or of lean mass when you give a body-fat %). " +
            "2.2 to 2.6 is typical while cutting, 1.6 to 2.2 otherwise."
    const val CARB_CYCLING =
        "More carbs on training days, fewer on rest days. The week adds up to the same total, so it only changes when you eat them."
    const val REFEED =
        "A day with carbs raised to your maintenance calories, paid for by slightly lower other days, " +
            "so the weekly total does not change."
    const val DIET_BREAK =
        "A planned week at maintenance calories every 8 to 12 weeks of dieting, to rest from the deficit."
    const val FLOOR =
        "Intake will not plan calories below 1.1 times your calories at rest unless you turn this on. " +
            "Eating that low for long is hard on muscle, energy and mood."
}
