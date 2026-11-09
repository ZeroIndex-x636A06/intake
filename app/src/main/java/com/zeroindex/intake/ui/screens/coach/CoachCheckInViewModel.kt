package com.zeroindex.intake.ui.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.coach.CoachReworder
import com.zeroindex.intake.data.coach.CoachRepository
import com.zeroindex.intake.data.db.entity.CoachSuggestionEntity
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.coach.CheckInRecord
import com.zeroindex.intake.domain.coach.CoachAction
import com.zeroindex.intake.domain.coach.WeekSummary
import com.zeroindex.intake.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PastCheckIn(val record: CheckInRecord, val rows: List<CoachSuggestionEntity>)

data class CheckInUiState(
    val loading: Boolean = true,
    val summary: WeekSummary? = null,
    val latest: PastCheckIn? = null,
    val past: List<PastCheckIn> = emptyList(),
    val weekday: Int = 1,
    val weekdayChosen: Boolean = false,
    val notify: Boolean = true,
    val aiReword: Boolean = false,
    val aiConfigured: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.POUNDS,
    val working: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class CoachCheckInViewModel @Inject constructor(
    private val coach: CoachRepository,
    private val reworder: CoachReworder,
    private val settings: SettingsRepository,
    private val gateway: AiGateway,
) : ViewModel() {

    private val summary = MutableStateFlow<WeekSummary?>(null)
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val weekday = MutableStateFlow(1)

    val state: StateFlow<CheckInUiState> = combine(
        coach.observeHistory(), settings.settings, summary, working, combine(message, weekday) { m, w -> m to w },
    ) { rows, s, sum, busy, (msg, day) ->
        val byDay = rows.groupBy { it.checkInDay }
        val records = s.coachState.checkIns.ifEmpty {
            byDay.keys.sortedDescending().map { CheckInRecord(it, byDay.getValue(it).size, "") }
        }
        val all = records.map { PastCheckIn(it, byDay[it.day].orEmpty().sortedBy { r -> r.priority }) }
        CheckInUiState(
            loading = false,
            summary = sum,
            latest = all.firstOrNull(),
            past = all.drop(1),
            weekday = day,
            weekdayChosen = s.coachWeekday != null,
            notify = s.coachNotify,
            aiReword = s.coachAiReword,
            aiConfigured = gateway.isConfigured(),
            weightUnit = s.weightUnit,
            working = busy,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInUiState())

    init {
        viewModelScope.launch {
            runCatching { coach.generateIfDue() }
            weekday.value = coach.weekday()
            summary.value = runCatching { coach.currentSummary() }.getOrNull()
        }
    }

    fun checkInNow() {
        viewModelScope.launch {
            working.value = true
            runCatching {
                coach.generate()
                settings.settings.first().coachState.lastCheckInDay?.let { reworder.rewordCheckIn(it) }
                summary.value = coach.currentSummary()
            }.onFailure { message.value = "Could not make the check-in. Nothing was changed." }
            working.value = false
        }
    }

    fun apply(row: CoachSuggestionEntity) {
        viewModelScope.launch {
            working.value = true
            val result = coach.apply(row.id)
            message.value = result.message
            summary.value = runCatching { coach.currentSummary() }.getOrNull()
            working.value = false
        }
    }

    fun dismiss(row: CoachSuggestionEntity) {
        viewModelScope.launch { coach.dismiss(row.id) }
    }

    fun clearMessage() {
        message.update { null }
    }

    fun setWeekday(isoDay: Int) {
        viewModelScope.launch {
            settings.setCoachWeekday(isoDay)
            weekday.value = isoDay
        }
    }

    fun setNotify(on: Boolean) {
        viewModelScope.launch { settings.setCoachNotify(on) }
    }

    fun setAiReword(on: Boolean) {
        viewModelScope.launch {
            settings.setCoachAiReword(on)
            if (on) {
                val day = state.value.latest?.record?.day
                if (day != null) runCatching { reworder.rewordCheckIn(day) }
            }
        }
    }

    companion object {
        fun describe(action: CoachAction): String = when (action) {
            is CoachAction.SetCalories -> "Calories ${action.fromKcal} to ${action.toKcal} kcal (${if (action.toKcal >= action.fromKcal) "+" else "−"}${kotlin.math.abs(action.toKcal - action.fromKcal)})"
            is CoachAction.Deload -> "${action.percent}% lighter weights for ${action.days} days" + (action.programName?.let { " in $it" } ?: "")
            is CoachAction.SwapExercise -> "${action.fromName} becomes ${action.toName} in your program"
            is CoachAction.EasierDay -> "${action.setsLess} fewer set per exercise for the next ${action.days} days"
            is CoachAction.MuscleSets -> "${if (action.deltaSets >= 0) "+" else "−"}${kotlin.math.abs(action.deltaSets)} sets a week for ${action.muscle}"
            is CoachAction.Acknowledge -> "No change to your plan"
        }
    }
}
