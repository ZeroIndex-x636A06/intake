package com.zeroindex.intake.ui.components

import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.Locale

/**
 * The one place every date picker in the app gets its state from, so the Food Diary, Training, plan and photo pickers
 * behave alike. It uses the device's default locale. An earlier version swapped the region to a Monday-first one (en-GB)
 * to make the week start on Monday (QA Q7-25), but that also changed the input format to dd/MM/yyyy and the headline to
 * "Tue, 24 Nov", which is worse for a US user than a Sunday-first week. The locale is therefore left alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppDatePickerState(
    initialSelectedDateMillis: Long?,
    selectableDates: SelectableDates = AnyDate,
): DatePickerState {
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    return remember(locale) {
        DatePickerState(
            locale = pickerLocale(locale),
            initialSelectedDateMillis = initialSelectedDateMillis,
            initialDisplayedMonthMillis = null,
            yearRange = 1900..2100,
            initialDisplayMode = DisplayMode.Picker,
            selectableDates = selectableDates,
        )
    }
}

private val AnyDate = object : SelectableDates {}

/** The locale the pickers use: the one given, unchanged. */
internal fun pickerLocale(deviceDefault: Locale): Locale = deviceDefault
