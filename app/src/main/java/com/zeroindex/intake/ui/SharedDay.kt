package com.zeroindex.intake.ui

import com.zeroindex.intake.domain.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The day that Today and Nutrition both show. One value for the whole app, so the day arrows on either tab move the
 * other tab too (IA-v5: "Day arrows share state with Today"). It starts at today each time the app starts.
 */
@Singleton
class SharedDay @Inject constructor(clock: Clock) {
    val date: MutableStateFlow<LocalDate> = MutableStateFlow(clock.today())
}
