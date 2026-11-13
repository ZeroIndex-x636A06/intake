@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    val full = remember(date) { date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        Surface(
            onClick = onPick,
            color = MaterialTheme.colorScheme.surface,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "$full. Choose a day" },
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
                if (label == "Today" || label == "Yesterday") {
                    Text(date.format(DateTimeFormatter.ofPattern("EEE, MMM d")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = if (canGoNext) "Next day" else "Next day, not available: today is the last day")
        }
        if (!isToday) {
            TextButton(onClick = onToday, modifier = Modifier.heightIn(min = 48.dp)) { Text("Today", fontWeight = FontWeight.ExtraBold) }
        }
    }
}

/** The date picker. Days after today are greyed out. Answers with the picker's UTC-midnight millis ([NutritionDay.fromPickerMillis]). */
@Composable
internal fun DayPickerDialog(date: LocalDate, today: LocalDate, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val lastPickable = remember(today) { NutritionDay.toPickerMillis(today) }
    val state = rememberDatePickerState(
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
