@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.zeroindex.intake.ui.components.LineButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import com.zeroindex.intake.ui.components.rememberMondayFirstDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The date, start time and length of a workout, as one block of fields. Used to log a past workout and to change the time of a
 * finished one. The date cannot be after [today]; the length is typed in minutes.
 */
@Composable
fun WhenFields(
    date: LocalDate,
    onDate: (LocalDate) -> Unit,
    timeState: TimePickerState,
    minutesText: String,
    onMinutes: (String) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    var pickingDate by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LineButton(
            onClick = { pickingDate = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Date: " + DateTimeFormatter.ofPattern("EEE d MMM yyyy").format(date)) }
        Text("Start time", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        TimeInput(state = timeState)
        OutlinedTextField(
            value = minutesText,
            onValueChange = { v -> onMinutes(v.filter { it.isDigit() }.take(4)) },
            label = { Text("Length (minutes)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (pickingDate) {
        val utc = ZoneId.of("UTC")
        val state = rememberMondayFirstDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(utc).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(utc).toLocalDate().isAfter(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(utc).toLocalDate()) }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}
