package com.zeroindex.intake.ui.components

import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * A Material date picker state whose weeks start on Monday, the week the rest of the app uses (week strip, reports,
 * Training calendar). The picker takes its first weekday from `WeekFields.of(locale)`, which on Android reads only the
 * language and region, so the Unicode "fw" extension (the first attempt, QA Q7-25) was ignored and an English (US)
 * phone started on Sunday. The state is built with an explicit locale ([mondayFirstLocale]) instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberMondayFirstDatePickerState(
    initialSelectedDateMillis: Long?,
    selectableDates: SelectableDates = AnyDate,
): DatePickerState {
    val base = androidx.compose.ui.text.intl.Locale.current.platformLocale
    return remember(base) {
        DatePickerState(
            locale = mondayFirstLocale(base),
            initialSelectedDateMillis = initialSelectedDateMillis,
            initialDisplayedMonthMillis = null,
            yearRange = 1900..2100,
            initialDisplayMode = DisplayMode.Picker,
            selectableDates = selectableDates,
        )
    }
}

private val AnyDate = object : SelectableDates {}

private val MondayRegions = listOf("GB", "IE", "DE", "FR", "NL", "ES", "IT")

internal fun firstDayOf(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

/**
 * [base] if its week already starts on Monday, otherwise the same language in a region whose week does. Only the region
 * changes, so month and weekday names stay in the user's language.
 */
internal fun mondayFirstLocale(base: Locale): Locale {
    if (firstDayOf(base) == DayOfWeek.MONDAY) return base
    val language = base.language.ifEmpty { "en" }
    return MondayRegions.map { Locale(language, it) }.firstOrNull { firstDayOf(it) == DayOfWeek.MONDAY }
        ?: Locale("en", "GB")
}
