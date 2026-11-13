package com.zeroindex.intake.domain.videos

/**
 * The "Workouts only" filter for Videos: a deterministic score over a video's category, title, description, length and
 * channel name. No model is involved and nothing leaves the phone. Every rule that fires is listed in
 * [VideoVerdict.signals], so a result is always explainable and each rule has a test.
 *
 * Score bands: 4 or more is a [VideoLabel.WORKOUT], 2 to 3 is [VideoLabel.MAYBE] (kept in "Workouts only" with a badge),
 * 1 or less is [VideoLabel.NOT_WORKOUT] (hidden until "Show everything"). The score is a pure function of the metadata:
 * "Show everything" only changes which rows are drawn, never a score.
 */
object WorkoutVideoClassifier {
    const val WORKOUT_AT = 4
    const val MAYBE_AT = 2

    /** Sports and Howto & Style lean positive (the brief); entertainment-type categories lean negative. */
    private val CATEGORY_POINTS = mapOf(
        "17" to 2, "26" to 1,
        "10" to -3, "20" to -3, "25" to -3, "23" to -2, "1" to -2, "24" to -1, "2" to -1, "15" to -1, "19" to -1, "28" to -1,
    )

    /** Words that almost only appear in training videos: +3 each, title total capped. */
    private val TITLE_STRONG = listOf(
        "workout", "work out", "hiit", "pilates", "yoga", "mobility", "stretch", "stretching", "follow along", "follow-along",
        "tabata", "emom", "amrap", "circuit", "calisthenics", "full body", "upper body", "lower body", "leg day", "push day",
        "pull day", "arm day", "no equipment", "warm up", "warm-up", "warmup", "cool down", "cooldown", "home workout",
        "strength training", "bodyweight", "kettlebell", "dumbbell", "barbell", "resistance band",
    )

    /** Words that hint at training but also appear elsewhere: +1 each, capped. */
    private val TITLE_WEAK = listOf(
        "routine", "training", "fitness", "gym", "core", "abs", "glutes", "burn", "lift", "lifting", "muscle", "cardio",
        "exercise", "exercises", "glute", "reps", "sets", "beginner", "beginners", "sweat", "legs", "arms", "shoulders", "back",
        "chest", "challenge", "program", "plan",
    )

    /** Everyday exercise names, so "Squat form" counts even if the catalog has it under another name. */
    private val EXERCISE_WORDS = listOf(
        "squat", "squats", "deadlift", "deadlifts", "lunge", "lunges", "push-up", "push up", "pushup", "pushups", "push-ups",
        "pull-up", "pull up", "pullup", "pullups", "pull-ups", "plank", "planks", "curl", "curls", "bench press", "overhead press",
        "shoulder press", "lateral raise", "lateral raises", "burpee", "burpees", "crunch", "crunches", "row", "rows",
        "hip thrust", "hip thrusts", "dip", "dips", "jumping jack", "jumping jacks", "mountain climber", "mountain climbers",
        "russian twist", "glute bridge", "leg press", "lat pulldown", "tricep extension", "face pull", "calf raise",
    )

    private val FORM_WORDS = listOf("how to", "form", "technique", "tutorial", "proper", "mistakes", "cues")

    /** Words that make a video something other than a workout: -4 in the title. */
    private val TITLE_NEGATIVE = listOf(
        "react", "vlog", "music video", "official video", "lyrics", "podcast", "unboxing", "news", "meme", "memes", "prank",
        "gameplay", "let's play", "lets play", "trailer", "what i eat", "haul", "storytime", "tier list", "interview",
        "playlist", "reaction", "review", "asmr", "mukbang", "highlights", "live stream", "livestream", "full movie",
        "workout music", "gym music", "motivation speech", "motivational speech",
    )

    private val DESCRIPTION_NEGATIVE = listOf("vlog", "podcast", "unboxing", "gameplay", "prank", "meme", "reaction")

    private val DESCRIPTION_POSITIVE = listOf(
        "workout", "exercise", "exercises", "reps", "sets", "equipment", "warm up", "warm-up", "hiit", "follow along",
        "rest between", "seconds on", "seconds off", "work interval", "muscles", "calories burned",
    )

    private val CHANNEL_POSITIVE = listOf(
        "fitness", "fit", "gym", "workout", "yoga", "pilates", "training", "strength", "crossfit", "coach", "athlean",
        "calisthenics", "mobility", "physio", "kinstretch", "bodybuilding", "powerlifting", "health", "athletic", "sweat",
    )

    private val CHANNEL_NEGATIVE = listOf(
        "music", "vevo", "records", "gaming", "games", "news", "tv", "podcast", "comedy", "entertainment", "movies", "trailers",
    )

    private val SETS_REPS = Regex("""\b\d{1,2}\s*[x×]\s*\d{1,3}\b|\b\d{1,2}\s*sets?\s*(?:of|x)\s*\d{1,3}\b""")
    private val MINUTES = Regex("""\b\d{1,3}\s*[- ]?\s*(?:min|mins|minute|minutes)\b""")

    fun classify(video: VideoMeta, catalogNames: Collection<String> = emptyList()): VideoVerdict {
        val signals = mutableListOf<String>()
        var score = 0
        fun add(points: Int, text: String) {
            score += points
            signals += "$text (${if (points >= 0) "+" else ""}$points)"
        }

        val title = normalise(video.title)
        val description = normalise(video.description)
        val channel = normalise(video.channelTitle)

        // Category.
        CATEGORY_POINTS[video.categoryId]?.let { add(it, "category ${categoryName(video.categoryId)}") }

        // Title: strong words (capped at +6), weak words (capped at +2), exercise names, counts.
        val strong = TITLE_STRONG.filter { containsWord(title, it) }
        if (strong.isNotEmpty()) add(minOf(6, strong.size * 3), "title says ${quote(strong.take(2))}")
        val weak = TITLE_WEAK.filter { containsWord(title, it) }
        if (weak.isNotEmpty() && strong.isEmpty()) add(minOf(2, weak.size), "title mentions ${quote(weak.take(2))}")
        val named = (EXERCISE_WORDS + catalogNames.map { normalise(it) }.filter { it.length >= 5 })
            .firstOrNull { containsWord(title, it) }
        if (named != null) add(2, "title names an exercise (\"$named\")")
        if (SETS_REPS.containsMatchIn(title)) add(2, "title has a sets and reps pattern")
        if (MINUTES.containsMatchIn(title) && (strong.isNotEmpty() || weak.isNotEmpty() || named != null)) add(1, "title gives a length in minutes")
        val formTutorial = named != null && FORM_WORDS.any { containsWord(title, it) }
        if (formTutorial) add(2, "title is a form tutorial")

        // Title: negative words.
        val negative = TITLE_NEGATIVE.filter { containsWord(title, it) || (it == "react" && Regex("""\breact\w*""").containsMatchIn(title)) }
        if (negative.isNotEmpty()) add(-4, "title says ${quote(negative.take(2))}")

        // Description.
        if (description.isNotEmpty()) {
            val dPos = DESCRIPTION_POSITIVE.count { containsWord(description, it) }
            if (dPos > 0) add(minOf(3, dPos), "description talks about training")
            val dNeg = DESCRIPTION_NEGATIVE.filter { containsWord(description, it) }
            if (dNeg.isNotEmpty() && negative.isEmpty()) add(-2, "description mentions ${quote(dNeg.take(2))}")
            if (ChapterParser.chapters(video.description).size >= 3) add(2, "description has a timestamped list")
        }

        // Length. A typical workout length only helps when the words already point at training.
        val textPositive = signals.any { it.contains("(+") && !it.startsWith("category") }
        val d = video.durationSec
        when {
            d <= 0 -> Unit
            d < 120 -> if (!formTutorial) add(-3, "under 2 minutes") else signals += "short, but a form tutorial (0)"
            d < 180 -> add(-1, "under 3 minutes")
            d in 300..5_400 && textPositive -> add(1, "${d / 60} minutes")
            d > 14_400 -> add(-3, "longer than 4 hours")
        }

        // Channel.
        if (channel.isNotEmpty()) {
            val cNeg = CHANNEL_NEGATIVE.firstOrNull { containsWord(channel, it) }
            val cPos = CHANNEL_POSITIVE.firstOrNull { channel.contains(it) }
            when {
                cNeg != null -> add(-2, "channel name has \"$cNeg\"")
                cPos != null -> add(2, "channel name has \"$cPos\"")
            }
        }

        val label = when {
            score >= WORKOUT_AT -> VideoLabel.WORKOUT
            score >= MAYBE_AT -> VideoLabel.MAYBE
            else -> VideoLabel.NOT_WORKOUT
        }
        return VideoVerdict(score, label, reasonFor(label, signals, video), signals)
    }

    private fun reasonFor(label: VideoLabel, signals: List<String>, video: VideoMeta): String {
        val positive = signals.filter { it.endsWith(")") && !it.contains("(-") && !it.contains("(0)") }
        val negative = signals.filter { it.contains("(-") }
        val length = if (video.durationSec > 0) ", ${YoutubeIds.clock(video.durationSec)}" else ""
        return when (label) {
            VideoLabel.WORKOUT -> "Looks like a workout: ${plain(positive.firstOrNull())}$length."
            VideoLabel.MAYBE -> "Might be a workout (${plain(positive.firstOrNull())}), but not sure."
            VideoLabel.NOT_WORKOUT ->
                if (negative.isNotEmpty()) "Hidden: ${plain(negative.first())}."
                else "Hidden: nothing in the title or description reads like exercise."
        }
    }

    private fun plain(signal: String?): String = signal?.substringBeforeLast(" (") ?: "no clear signals"

    private fun quote(words: List<String>) = words.joinToString(" and ") { "\"$it\"" }

    private fun categoryName(id: String) = when (id) {
        "17" -> "Sports"; "26" -> "Howto & Style"; "10" -> "Music"; "20" -> "Gaming"; "25" -> "News"; "23" -> "Comedy"
        "1" -> "Film"; "24" -> "Entertainment"; "2" -> "Autos"; "15" -> "Pets"; "19" -> "Travel"; "28" -> "Science & Tech"
        else -> id
    }

    private fun normalise(text: String): String = text.lowercase()
        .replace('’', '\'').replace('‘', '\'')

    /** Whole-word (or whole-phrase) match, so "abs" is not found in "absolutely". */
    internal fun containsWord(text: String, word: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(word, from)
            if (i < 0) return false
            val before = if (i == 0) ' ' else text[i - 1]
            val end = i + word.length
            fun boundaryAt(pos: Int) = pos >= text.length || !text[pos].isLetterOrDigit()
            // A plural ("workouts", "stretches") counts as the word.
            val ok = boundaryAt(end) ||
                (text.startsWith("s", end) && boundaryAt(end + 1)) ||
                (text.startsWith("es", end) && boundaryAt(end + 2))
            if (!before.isLetterOrDigit() && ok) return true
            from = i + 1
        }
    }
}
