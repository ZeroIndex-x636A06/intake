package com.zeroindex.intake.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * Runs [content] with the first day of the week set to Monday, the week the rest of the app uses (week strip, reports,
 * Training calendar). A Material date picker takes its first weekday from the locale, which made it start on Sunday for
 * an English (US) phone while the other calendars started on Monday (QA Q7-25). The locale keeps its language and
 * formats; only the "fw" (first weekday) Unicode extension is added.
 */
@Composable
fun MondayFirstWeek(content: @Composable () -> Unit) {
    val base = LocalConfiguration.current
    val config = remember(base) {
        Configuration(base).also { c ->
            c.setLocale(withMondayStart(base.locales.get(0) ?: Locale.getDefault()))
        }
    }
    CompositionLocalProvider(LocalConfiguration provides config, content = content)
}

internal fun withMondayStart(locale: Locale): Locale =
    Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("fw", "mon").build()
