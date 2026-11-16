package com.zeroindex.intake.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.settings.WeeklySets
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.training.RegionVolume
import com.zeroindex.intake.domain.training.WeeklyVolume
import com.zeroindex.intake.domain.training.log.StatSet
import com.zeroindex.intake.domain.training.log.TrainingStats
import com.zeroindex.intake.domain.training.log.TrainingStatsData
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class TrainingStatsUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val stats: TrainingStatsData = TrainingStatsData(emptyList(), emptyList()),
    /** This calendar week's weighted sets per body region, against the person's targets. */
    val volume: List<RegionVolume> = emptyList(),
)

/** The training block on Progress and the Training stats screen: estimated 1RM, recent records, this week's sets. */
@HiltViewModel
class TrainingStatsViewModel @Inject constructor(
    repository: TrainingRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val sets: Flow<TrainingStatsData> = combine(
        repository.observeSessions(),
        repository.observeAllSets(),
        repository.observeExercises(),
    ) { sessions, logged, exercises ->
        val start = sessions.associate { it.id to it.startedAt }
        val names = exercises.associate { it.id to it.name }
        TrainingStats.build(
            logged.filter { it.sessionId in start }.map {
                StatSet(
                    it.sessionId, start.getValue(it.sessionId), it.exerciseId, names[it.exerciseId] ?: "Exercise",
                    it.setIndex, it.weightKg, it.reps, it.isWarmup, it.completedAt,
                )
            },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val volume: Flow<Pair<AppSettings, List<RegionVolume>>> = settingsRepository.settings.flatMapLatest { app ->
        val range = WeeklyVolume.weekRange(clock.nowMillis(), clock.zone())
        val targets = WeeklySets.parse(app.weeklySetsCsv).mapKeys { (k, _) -> BodyRegion.valueOf(k.name) }
        repository.observeVolumeSets(range).let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { emit(app to WeeklyVolume.compute(it, targets, range)) }
            }
        }
    }

    val uiState: StateFlow<TrainingStatsUiState> = combine(sets, volume) { stats, (app, vol) ->
        TrainingStatsUiState(true, app, stats, vol)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingStatsUiState())
}
