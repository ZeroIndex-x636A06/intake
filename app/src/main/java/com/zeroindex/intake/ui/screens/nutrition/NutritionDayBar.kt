@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.nutrition

import com.zeroindex.intake.domain.time.DateText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.IconCircleButton
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.nutrition.NutritionDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ‹ date › at the top of the Nutrition tab. The date opens a date picker; "Today" jumps back when another day is shown. The
 * next arrow stops at today: future days are blocked (see [NutritionDay]).
 */
@Composable
internal fun DaySwitcher(
    label: String,
    date: LocalDate,
    isToday: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val full = remember(date) { DateText.mediumWithWeekday(date) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconCircleButton(IntakeIcons.ChevronLeft, "Previous day", onPrevious)
        Surface(
            onClick = onPick,
            color = Color.Transparent,
            shape = IntakeShape.Pill,
            modifier = Modifier.weight(1f).heightIn(min = IntakeSpacing.TouchTarget).semantics { contentDescription = "$full. Choose a day" },
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                    if (label == "Today" || label == "Yesterday") {
                        Text(DateText.mediumWithWeekday(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(IntakeIcons.ChevronDown, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(IconSize.Small))
            }
        }
        IconCircleButton(
            IntakeIcons.ChevronRight,
            if (canGoNext) "Next day" else "Next day, not available: today is the last day",
            onNext,
            enabled = canGoNext,
        )
        if (!isToday) {
            GhostButton(onClick = onToday) { Text("Today", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** The date picker. Days after today are greyed out. Answers with the picker's UTC-midnight millis ([NutritionDay.fromPickerMillis]). */
@Composable
internal fun DayPickerDialog(date: LocalDate, today: LocalDate, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val lastPickable = remember(today) { NutritionDay.toPickerMillis(today) }
    val state = com.zeroindex.intake.ui.components.rememberAppDatePickerState(
        initialSelectedDateMillis = NutritionDay.toPickerMillis(date),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= lastPickable
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let(onPick) ?: onDismiss() }) { Text("Show this day") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}
