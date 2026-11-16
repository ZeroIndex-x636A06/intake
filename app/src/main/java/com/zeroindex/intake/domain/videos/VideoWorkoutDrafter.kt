package com.zeroindex.intake.domain.videos

/** Where a draft came from. */
enum class DraftSource(val label: String) {
    CHAPTERS("Read from the video's chapters"),
    DESCRIPTION("Read from lines of the description"),
    AI("AI suggested"),
    NOTHING("Nothing found"),
}

enum class DraftKind(val label: String) {
    /** Matched to an exercise in the library. */
    MATCHED("Matched"),

    /** Looks like an exercise but is not in the library: it will be created when saved. */
    NEW("New, will be created"),

    /** Text that is probably not an exercise (an intro, a thank-you): left out unless the person turns it on. */
    UNMATCHED("Skip"),
}

/** One row of the review list. The person can edit every field before saving. */
data class DraftItem(
    val key: Int,
    val text: String,
    val kind: DraftKind,
    val exerciseId: String?,
    val exerciseName: String,
    val sets: Int,
    /** Reps per set; 1 for timed work. */
    val reps: Int,
    /** Work time in seconds when the exercise is timed (a plank, an interval); null for counted reps. */
    val seconds: Int?,
    val restSec: Int,
    val include: Boolean,
    val startSec: Int? = null,
    val primaryMuscle: String = "core",
)

data class VideoWorkoutDraft(val source: DraftSource, val items: List<DraftItem>) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Chapters and description lines to a workout draft: parse, match to the library, give every row sensible numbers.
 * Everything here is deterministic; the optional AI step replaces the draft, it does not feed this one.
 */
object VideoWorkoutDrafter {
    const val DEFAULT_SETS = 3
    const val DEFAULT_REPS = 10
    const val DEFAULT_REST_SEC = 60

    /** Chapter titles that are about the video, not an exercise. */
    private val NOT_EXERCISE = listOf(
        "intro", "introduction", "outro", "welcome", "thanks", "thank you", "subscribe", "sponsor", "recap", "summary",
        "conclusion", "end", "ending", "credits", "overview", "what you need", "equipment", "preview", "announcement", "ad",
        "disclaimer", "tips", "bonus", "q&a", "final thoughts",
    )

    fun draft(video: VideoMeta, library: List<ExerciseEntry>): VideoWorkoutDraft {
        val matcher = ExerciseNameMatcher(library)
        val chapters = ChapterParser.chapters(video.description)
        if (chapters.isNotEmpty()) {
            val lines = chapters.mapIndexedNotNull { i, c ->
                val length = (chapters.getOrNull(i + 1)?.startSec ?: video.durationSec.takeIf { it > c.startSec }) ?.minus(c.startSec)
                val parsed = ExerciseLineParser.parse(c.title, c.startSec) ?: return@mapIndexedNotNull null
                // A timed block with no numbers of its own: the chapter's length is the work time.
                val timed = if (parsed.reps == null && parsed.seconds == null && length != null && length in 15..600) {
                    parsed.copy(seconds = length)
                } else {
                    parsed
                }
                timed
            }
            return VideoWorkoutDraft(DraftSource.CHAPTERS, build(lines, matcher, fromChapters = true))
        }
        val lines = video.description.lines().mapNotNull { raw ->
            val t = raw.trim().trimStart('-', '•', '*', '·', '▶', '►', ' ')
            if (t.length < 3 || t.length > 90 || !ExerciseLineParser.hasPrescription(t)) null
            else ExerciseLineParser.parse(t)
        }.filter { matcher.match(it.name) != null || it.reps != null || it.sets != null }
        if (lines.isEmpty()) return VideoWorkoutDraft(DraftSource.NOTHING, emptyList())
        return VideoWorkoutDraft(DraftSource.DESCRIPTION, build(lines, matcher, fromChapters = false))
    }

    /**
     * A draft from lines that something else already read (the optional AI step): matched to the library the same way, and
     * marked with [source] so the review list says where it came from. The person still reviews every row.
     */
    fun fromLines(lines: List<ParsedExerciseLine>, library: List<ExerciseEntry>, source: DraftSource): VideoWorkoutDraft {
        val matcher = ExerciseNameMatcher(library)
        return VideoWorkoutDraft(source, build(lines, matcher, fromChapters = false))
    }

    private fun build(lines: List<ParsedExerciseLine>, matcher: ExerciseNameMatcher, fromChapters: Boolean): List<DraftItem> =
        lines.mapIndexed { i, line -> item(i, line, matcher, fromChapters) }

    private fun item(key: Int, line: ParsedExerciseLine, matcher: ExerciseNameMatcher, fromChapters: Boolean): DraftItem {
        val match = matcher.match(line.name)
        val aboutTheVideo = NOT_EXERCISE.any { WorkoutVideoClassifier.containsWord(line.name.lowercase(), it) }
        val numbers = line.reps != null || line.sets != null || line.seconds != null
        val kind = when {
            match != null -> DraftKind.MATCHED
            aboutTheVideo -> DraftKind.UNMATCHED
            // A chapter with no library match and no numbers is probably "Warm up" or a talking section.
            fromChapters && line.reps == null && line.sets == null && line.restSec == null && !looksLikeExercise(line.name) -> DraftKind.UNMATCHED
            numbers || looksLikeExercise(line.name) -> DraftKind.NEW
            else -> DraftKind.UNMATCHED
        }
        val timed = line.seconds != null && line.reps == null
        val name = match?.entry?.name ?: line.name.replaceFirstChar { it.uppercase() }
        return DraftItem(
            key = key,
            text = line.rawText,
            kind = kind,
            exerciseId = match?.entry?.id,
            exerciseName = name,
            sets = line.sets ?: if (timed && fromChapters) 1 else DEFAULT_SETS,
            reps = if (timed) 1 else line.reps ?: DEFAULT_REPS,
            seconds = if (timed) line.seconds else null,
            restSec = line.restSec ?: if (timed && fromChapters) 0 else DEFAULT_REST_SEC,
            include = kind != DraftKind.UNMATCHED && !(aboutTheVideo),
            startSec = line.startSec,
            primaryMuscle = MuscleGuess.of(line.name),
        )
    }

    private fun looksLikeExercise(name: String): Boolean {
        val n = name.lowercase()
        return EXERCISE_HINTS.any { WorkoutVideoClassifier.containsWord(n, it) }
    }

    private val EXERCISE_HINTS = listOf(
        "squat", "lunge", "press", "curl", "row", "raise", "plank", "crunch", "push up", "pushup", "pull up", "pullup", "dip",
        "deadlift", "bridge", "thrust", "burpee", "jack", "climber", "twist", "kick", "extension", "fly", "flye", "pulldown",
        "shrug", "stretch", "hold", "hinge", "swing", "snatch", "clean", "jump", "step up", "sit up", "situp", "bird dog",
        "dead bug", "superman", "pose", "cat cow", "child", "downward dog", "cobra", "warrior",
    )
}

/** A rough first guess at the muscle of a new exercise; the person can change it. Never used for allergens or targets. */
object MuscleGuess {
    private val RULES = listOf(
        listOf("squat", "lunge", "leg press", "step up", "leg extension", "wall sit", "pistol") to "quads",
        listOf("deadlift", "hamstring", "hinge", "good morning", "leg curl", "swing") to "hamstrings",
        listOf("glute", "bridge", "thrust", "donkey", "fire hydrant", "clam") to "glutes",
        listOf("calf", "calves") to "calves",
        listOf("bench", "chest", "push up", "pushup", "fly", "flye", "dip") to "chest",
        listOf("overhead", "shoulder", "lateral", "arnold", "front raise", "rear delt", "face pull") to "shoulders",
        listOf("row", "pull up", "pullup", "pulldown", "lat ", "chin up", "superman", "back") to "back",
        listOf("tricep", "skull", "kickback", "close grip") to "triceps",
        listOf("bicep", "curl", "hammer") to "biceps",
        listOf("shrug", "trap") to "traps",
        listOf("plank", "crunch", "sit up", "situp", "twist", "ab ", "abs", "core", "dead bug", "bird dog", "mountain climber", "leg raise") to "core",
    )

    fun of(name: String): String {
        val n = " " + name.lowercase() + " "
        return RULES.firstOrNull { (words, _) -> words.any { n.contains(it) } }?.second ?: "core"
    }
}
