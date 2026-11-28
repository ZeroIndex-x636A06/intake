@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.screens.train

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.training.history.BackdatedLog
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.IntakeChip
import java.time.LocalDate
import java.time.LocalTime

/**
 * "Log a past workout": pick the day it was, when it started, how long it lasted and (optionally) which day of the program it
 * was. Saving creates a finished workout; its sets are added on its own page.
 */
@Composable
fun PastWorkoutDialog(
    today: LocalDate,
    days: List<DayChoice>,
    problemWith: (LocalDate, LocalTime, Int?) -> String?,
    onCreate: (date: LocalDate, time: LocalTime, minutes: Int, dayId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf(today) }
    var minutesText by remember { mutableStateOf(BackdatedLog.DEFAULT_DURATION_MIN.toString()) }
    var dayId by remember { mutableStateOf<String?>(null) }
    // A start that is already in the past, so Log it is not disabled the moment the form opens (Q8-7).
    val initialStart = remember { BackdatedLog.defaultStart(today, System.currentTimeMillis(), java.time.ZoneId.systemDefault()) }
    val timeState = rememberTimePickerState(
        initialHour = initialStart.hour,
        initialMinute = initialStart.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    val time = LocalTime.of(timeState.hour, timeState.minute)
    val minutes = minutesText.toIntOrNull()
    val problem = problemWith(date, time, minutes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a past workout") },
        text = {
            Column {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "For a workout you did but did not record. Pick when it was, then add its sets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WhenFields(date, { date = it }, timeState, minutesText, { minutesText = it }, today)
                Text("Which workout", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntakeChip("Blank workout", tone = if (dayId == null) ChipTone.Ink else ChipTone.Neutral, onClick = { dayId = null })
                    days.forEach { d ->
                        IntakeChip(d.name, tone = if (dayId == d.id) ChipTone.Ink else ChipTone.Neutral, onClick = { dayId = d.id })
                    }
                }
            }
            // The message sits outside the scrolling part, so it is never cut off below the time picker (Q7-5).
            problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(date, time, minutes ?: return@TextButton, dayId) }, enabled = problem == null) { Text("Log it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
