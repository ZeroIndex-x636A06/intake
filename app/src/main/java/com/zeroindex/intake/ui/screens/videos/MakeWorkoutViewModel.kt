package com.zeroindex.intake.ui.screens.videos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.video.VideoWorkoutAi
import com.zeroindex.intake.data.remote.youtube.VideoError
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.video.VideoRepository
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.videos.ChapterParser
import com.zeroindex.intake.domain.videos.DraftItem
import com.zeroindex.intake.domain.videos.DraftKind
import com.zeroindex.intake.domain.videos.DraftSource
import com.zeroindex.intake.domain.videos.ExerciseEntry
import com.zeroindex.intake.domain.videos.MuscleGuess
import com.zeroindex.intake.domain.videos.ParsedExerciseLine
import com.zeroindex.intake.domain.videos.VideoMeta
import com.zeroindex.intake.domain.videos.VideoWorkoutDrafter
import com.zeroindex.intake.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MakeWorkoutState(
    val loading: Boolean = true,
    val videoId: String = "",
    val meta: VideoMeta? = null,
    val name: String = "",
    val source: DraftSource = DraftSource.NOTHING,
    val items: List<DraftItem> = emptyList(),
    val hasDescription: Boolean = false,
    val aiAvailable: Boolean = false,
    /** A workout is already running: only one session at a time, so "Save and start" is off. */
    val workoutRunning: Boolean = false,
    val aiBusy: Boolean = false,
    val aiStartedAt: Long = 0L,
    val aiError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val includedCount: Int get() = items.count { it.include && it.exerciseName.isNotBlank() }
}

/**
 * Make this a workout (IA v5 7c). The draft is read on this phone first (chapters, then lines of the description) and
 * matched to the exercise library; the person reviews and edits every row. "Improve with AI" is a separate tap that runs
 * through the AI gateway; its answer only replaces the draft, and nothing is saved until Save.
 */
@HiltViewModel
class MakeWorkoutViewModel @Inject constructor(
    private val videos: VideoRepository,
    private val training: TrainingRepository,
    private val ai: VideoWorkoutAi,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val videoId: String = savedStateHandle.toRoute<Route.MakeWorkout>().videoId

    private val _state = MutableStateFlow(MakeWorkoutState(videoId = videoId))
    val state: StateFlow<MakeWorkoutState> = _state.asStateFlow()

    private var library: List<ExerciseEntry> = emptyList()
    private var aiJob: Job? = null

    init {
        viewModelScope.launch {
            library = training.exercises().map { ExerciseEntry(it.id, it.name) }
            var error: String? = null
            val meta = try {
                videos.video(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = if (e is VideoError) e.message else "Could not reach YouTube just now."
                null
            }
            val video = meta ?: VideoMeta(id = videoId, title = "YouTube video")
            val draft = VideoWorkoutDrafter.draft(video, library)
            _state.value = MakeWorkoutState(
                loading = false,
                videoId = videoId,
                meta = video,
                name = video.title.take(60),
                source = draft.source,
                items = draft.items,
                hasDescription = video.description.isNotBlank(),
                aiAvailable = ai.isAvailable(),
                workoutRunning = training.observeInProgress().first() != null,
                error = error,
            )
        }
    }

    fun setName(text: String) = _state.update { it.copy(name = text) }

    fun edit(key: Int, change: (DraftItem) -> DraftItem) =
        _state.update { s -> s.copy(items = s.items.map { if (it.key == key) change(it) else it }) }

    fun move(key: Int, delta: Int) = _state.update { s ->
        val list = s.items.toMutableList()
        val from = list.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@update s
        list.add(to, list.removeAt(from))
        s.copy(items = list)
    }

    fun remove(key: Int) = _state.update { s -> s.copy(items = s.items.filter { it.key != key }) }

    /** A blank row for an exercise the video did not name. */
    fun addBlank() = _state.update { s ->
        val key = (s.items.maxOfOrNull { it.key } ?: -1) + 1
        s.copy(
            items = s.items + DraftItem(
                key = key, text = "", kind = DraftKind.NEW, exerciseId = null, exerciseName = "", sets = VideoWorkoutDrafter.DEFAULT_SETS,
                reps = VideoWorkoutDrafter.DEFAULT_REPS, seconds = null, restSec = VideoWorkoutDrafter.DEFAULT_REST_SEC, include = true,
            ),
        )
    }

    /** Points a row at an exercise the person picked from the library. */
    fun pickExercise(key: Int, exerciseId: String) = viewModelScope.launch {
        val e = training.exerciseAnyState(exerciseId) ?: return@launch
        edit(key) { it.copy(exerciseId = e.id, exerciseName = e.name, kind = DraftKind.MATCHED, primaryMuscle = e.primaryMuscle, include = true) }
    }

    /** Rename a row that will be created (not a library match): its muscle guess follows the name until it is changed. */
    fun rename(key: Int, name: String) = edit(key) { it.copy(exerciseName = name, primaryMuscle = MuscleGuess.of(name), exerciseId = null, kind = DraftKind.NEW) }

    /** "Improve with AI": one explicit tap, one gateway call. The answer replaces the list, marked "AI suggested". */
    fun improveWithAi() {
        val s = _state.value
        val meta = s.meta ?: return
        if (s.aiBusy) return
        _state.update { it.copy(aiBusy = true, aiError = null, aiStartedAt = clock.nowMillis()) }
        aiJob = viewModelScope.launch {
            try {
                val chapters = ChapterParser.chapters(meta.description)
                val result = ai.improve(meta, chapters)
                if (result.isEmpty()) {
                    _state.update { it.copy(aiBusy = false, aiError = "AI did not find a workout in this video's title and description.") }
                } else {
                    val lines = result.map {
                        ParsedExerciseLine(
                            rawText = it.name, name = it.name, sets = it.sets.takeIf { n -> n > 0 }, reps = it.reps.takeIf { n -> n > 0 },
                            seconds = it.seconds, restSec = it.restSec,
                        )
                    }
                    val draft = VideoWorkoutDrafter.fromLines(lines, library, DraftSource.AI)
                    _state.update { it.copy(aiBusy = false, source = DraftSource.AI, items = draft.items) }
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(aiBusy = false) }
                throw e
            } catch (e: AiError) {
                _state.update { it.copy(aiBusy = false, aiError = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(aiBusy = false, aiError = "AI could not read the video text. Nothing was changed.") }
            }
        }
    }

    fun cancelAi() { aiJob?.cancel() }

    /** Saves the reviewed list as a My workout linked to the video. [start] also opens a session on it. */
    fun save(start: Boolean, onSaved: (programId: String, sessionId: String?) -> Unit) {
        val s = _state.value
        if (s.saving || s.includedCount == 0) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val program = training.saveVideoWorkout(s.name, videoId, s.items)
            if (program == null) {
                _state.update { it.copy(saving = false, error = "Turn on at least one exercise to save.") }
                return@launch
            }
            val session = if (start) {
                training.days(program.id).firstOrNull()?.let { training.startSession(it.id).id }
            } else {
                null
            }
            _state.update { it.copy(saving = false) }
            onSaved(program.id, session)
        }
    }

    /** "Add by hand": an empty workout linked to the video, opened in the builder. */
    fun addByHand(onCreated: (programId: String) -> Unit) {
        val s = _state.value
        viewModelScope.launch { onCreated(training.createVideoWorkout(s.name, videoId).id) }
    }
}
