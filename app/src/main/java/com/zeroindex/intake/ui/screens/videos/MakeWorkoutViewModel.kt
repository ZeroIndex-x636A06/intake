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
import com.zeroindex.intake.data.video.direct.TranscriptRepository
import com.zeroindex.intake.data.video.direct.TranscriptResult
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.videos.ChapterParser
import com.zeroindex.intake.domain.videos.DraftItem
import com.zeroindex.intake.domain.videos.DraftKind
import com.zeroindex.intake.domain.videos.DraftSource
import com.zeroindex.intake.domain.videos.ExerciseEntry
import com.zeroindex.intake.domain.videos.MuscleGuess
import com.zeroindex.intake.domain.videos.ParsedExerciseLine
import com.zeroindex.intake.domain.videos.Transcript
import com.zeroindex.intake.domain.videos.TranscriptParser
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
    /** How many exercises the last pasted text gave, or null when nothing has been pasted. */
    val pastedFound: Int? = null,
    val hasDescription: Boolean = false,
    val aiAvailable: Boolean = false,
    /** A workout is already running: only one session at a time, so "Save and start" is off. */
    val workoutRunning: Boolean = false,
    val aiBusy: Boolean = false,
    val aiStartedAt: Long = 0L,
    val aiError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    /** The captions the drafts were read with (fetched with yt-dlp, or pasted with times), or null. */
    val transcript: Transcript? = null,
    /** True when the yt-dlp opt-in (Settings > Videos) is on, so "Get transcript" can run. */
    val directOn: Boolean = false,
    val transcriptBusy: Boolean = false,
    /** Plain-words result of the last "Get transcript" tap (no captions, failed, ...), or null. */
    val transcriptNote: String? = null,
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
    private val transcripts: TranscriptRepository,
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
            // A transcript fetched earlier is read from app storage: no network, no yt-dlp, no tap needed.
            val transcript = transcripts.cached(videoId)
            val draft = VideoWorkoutDrafter.draft(video, library, transcript)
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
                transcript = transcript,
                directOn = transcripts.directOn(),
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
    /**
     * The person pasted what the video itself says (its transcript, or the exercise list from a pinned comment). YouTube
     * gives an app only the title and description, never the spoken or on-screen content, so this is how that content
     * gets in. It is read on this phone like a description, and it is what "Improve with AI" is sent if that is tapped.
     */
    /**
     * "Get transcript": one explicit tap. Asks yt-dlp for the video's captions (Settings > Videos opt-in; no API key), keeps
     * them on the phone and re-reads the draft with them. Nothing here talks to AI.
     */
    fun getTranscript() {
        val s = _state.value
        val meta = s.meta ?: return
        if (s.transcriptBusy) return
        _state.update { it.copy(transcriptBusy = true, transcriptNote = null) }
        viewModelScope.launch {
            val result = try {
                transcripts.fetch(videoId)
            } catch (e: CancellationException) {
                _state.update { it.copy(transcriptBusy = false) }
                throw e
            }
            when (result) {
                is TranscriptResult.Found -> applyTranscript(meta, result.transcript)
                TranscriptResult.NotEnabled -> _state.update {
                    it.copy(transcriptBusy = false, directOn = false, transcriptNote = "Turn on direct playback in Settings to fetch transcripts automatically.")
                }
                TranscriptResult.NoCaptions -> _state.update {
                    it.copy(transcriptBusy = false, transcriptNote = "This video has no captions in English or your language. You can paste a transcript below.")
                }
                is TranscriptResult.Failed -> _state.update { it.copy(transcriptBusy = false, transcriptNote = result.message) }
            }
        }
    }

    private fun applyTranscript(meta: VideoMeta, transcript: Transcript) {
        val draft = VideoWorkoutDrafter.draft(meta, library, transcript)
        _state.update {
            it.copy(
                transcriptBusy = false,
                transcript = transcript,
                source = draft.source,
                items = draft.items.ifEmpty { it.items },
                transcriptNote = if (draft.items.isEmpty()) "Got the captions (${transcript.segments.size} lines) but found no exercises in them. You can still add them by hand or ask AI." else null,
            )
        }
    }

    fun usePastedText(text: String) {
        val meta = _state.value.meta ?: return
        // A transcript copied from YouTube has times: keep them, so each exercise knows where it is in the video.
        val timed = TranscriptParser.parsePasted(text)
        if (timed.isNotEmpty()) {
            val t = Transcript(videoId, "pasted", auto = false, segments = timed)
            val draft = VideoWorkoutDrafter.draft(meta, library, t)
            _state.update { it.copy(transcript = t, source = draft.source, items = draft.items.ifEmpty { it.items }, pastedFound = draft.items.size) }
            return
        }
        val pasted = text.trim().take(MAX_PASTE)
        if (pasted.isEmpty()) return
        // Pasted text goes first: it is what the person chose to give, and the AI step reads only the start of long text.
        val video = meta.copy(description = (pasted + "\n" + meta.description).trim())
        val draft = VideoWorkoutDrafter.draft(video, library)
        _state.update {
            it.copy(meta = video, source = draft.source, items = draft.items.ifEmpty { it.items }, hasDescription = true, pastedFound = draft.items.size)
        }
    }

    fun improveWithAi() {
        val s = _state.value
        val meta = s.meta ?: return
        if (s.aiBusy) return
        _state.update { it.copy(aiBusy = true, aiError = null, aiStartedAt = clock.nowMillis()) }
        aiJob = viewModelScope.launch {
            try {
                val chapters = ChapterParser.chapters(meta.description)
                val result = ai.improve(meta, chapters, s.transcript)
                if (result.isEmpty()) {
                    _state.update { it.copy(aiBusy = false, aiError = "AI did not find a workout in this video's title and description.") }
                } else {
                    val lines = result.map {
                        ParsedExerciseLine(
                            rawText = it.name, name = it.name, sets = it.sets.takeIf { n -> n > 0 }, reps = it.reps.takeIf { n -> n > 0 },
                            seconds = it.seconds, restSec = it.restSec, startSec = it.startSec?.toInt()?.takeIf { t -> t >= 0 },
                        )
                    }
                    val draft = VideoWorkoutDrafter.fromLines(lines, library, DraftSource.AI)
                    // A time the model left out is kept from the deterministic draft when the same exercise was in it.
                    val before = _state.value.items
                    val items = draft.items.map { n ->
                        if (n.startSec != null) n else n.copy(startSec = before.firstOrNull { b ->
                            (n.exerciseId != null && n.exerciseId == b.exerciseId) || b.exerciseName.equals(n.exerciseName, ignoreCase = true)
                        }?.startSec)
                    }
                    _state.update { it.copy(aiBusy = false, source = DraftSource.AI, items = items) }
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

    private companion object {
        /** A long video's transcript is tens of thousands of characters; this is plenty for the exercise names. */
        const val MAX_PASTE = 20_000
    }
}
