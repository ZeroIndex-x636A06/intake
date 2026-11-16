package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import org.junit.Test

class ChaptersAndMatchingTest {

    private val library = ExerciseCatalog.ALL.map { ExerciseEntry(it.id, it.name) }
    private val matcher = ExerciseNameMatcher(library)

    // --- chapters -------------------------------------------------------------------------------------------

    @Test
    fun `reads timestamp lines in order`() {
        val chapters = ChapterParser.chapters("Follow along!\n\n0:00 Warm up\n1:30 Squats x 12\n1:02:05 Cool down")
        assertThat(chapters.map { it.startSec }).containsExactly(0, 90, 3725).inOrder()
        assertThat(chapters.map { it.title }).containsExactly("Warm up", "Squats x 12", "Cool down").inOrder()
    }

    @Test
    fun `accepts bullets dashes and numbering around the time`() {
        val text = "- 0:00 - Intro\n• 0:45: Push-ups\n3) 2:10 | Plank\n▶ 3:00 Lunges"
        assertThat(ChapterParser.chapters(text).map { it.title }).containsExactly("Intro", "Push-ups", "Plank", "Lunges").inOrder()
    }

    @Test
    fun `reads a time at the end of the line`() {
        val chapters = ChapterParser.chapters("Squats - 0:30\nLunges - 1:15")
        assertThat(chapters.map { it.title }).containsExactly("Squats", "Lunges").inOrder()
        assertThat(chapters.map { it.startSec }).containsExactly(30, 75).inOrder()
    }

    @Test
    fun `one timestamp is not a chapter list`() {
        assertThat(ChapterParser.chapters("Watch my other video at 2:30 for more")).isEmpty()
    }

    @Test
    fun `lines whose time does not go up are ignored`() {
        val chapters = ChapterParser.chapters("0:00 A\n2:00 B\n1:00 C\n3:00 D")
        assertThat(chapters.map { it.title }).containsExactly("A", "B", "D").inOrder()
    }

    @Test
    fun `no timestamps gives nothing`() {
        assertThat(ChapterParser.chapters("Just a description with no times")).isEmpty()
        assertThat(ChapterParser.chapters("")).isEmpty()
    }

    // --- one line -------------------------------------------------------------------------------------------

    @Test
    fun `parses x reps`() {
        val p = ExerciseLineParser.parse("Squats x 12")!!
        assertThat(p.name).isEqualTo("Squats")
        assertThat(p.reps).isEqualTo(12)
        assertThat(p.sets).isNull()
    }

    @Test
    fun `parses sets x reps in both orders`() {
        val a = ExerciseLineParser.parse("Lunges 3x12")!!
        assertThat(a.name).isEqualTo("Lunges"); assertThat(a.sets).isEqualTo(3); assertThat(a.reps).isEqualTo(12)
        val b = ExerciseLineParser.parse("4 x 8 Bench press")!!
        assertThat(b.name).isEqualTo("Bench press"); assertThat(b.sets).isEqualTo(4); assertThat(b.reps).isEqualTo(8)
    }

    @Test
    fun `parses sets of reps in words`() {
        val p = ExerciseLineParser.parse("Goblet squat - 3 sets of 10")!!
        assertThat(p.name).isEqualTo("Goblet squat"); assertThat(p.sets).isEqualTo(3); assertThat(p.reps).isEqualTo(10)
    }

    @Test
    fun `parses seconds and minutes as work time`() {
        assertThat(ExerciseLineParser.parse("Plank 45 sec")!!.seconds).isEqualTo(45)
        assertThat(ExerciseLineParser.parse("Plank (45s)")!!.seconds).isEqualTo(45)
        assertThat(ExerciseLineParser.parse("2 min wall sit")!!.seconds).isEqualTo(120)
    }

    @Test
    fun `parses work and rest intervals`() {
        val p = ExerciseLineParser.parse("Burpees 40s on / 20s off")!!
        assertThat(p.name).isEqualTo("Burpees"); assertThat(p.seconds).isEqualTo(40); assertThat(p.restSec).isEqualTo(20)
        val q = ExerciseLineParser.parse("Jumping jacks 45 sec on 15 off")!!
        assertThat(q.seconds).isEqualTo(45); assertThat(q.restSec).isEqualTo(15)
    }

    @Test
    fun `a plain name has no numbers`() {
        val p = ExerciseLineParser.parse("Push-ups")!!
        assertThat(p.name).isEqualTo("Push-ups")
        assertThat(p.reps).isNull(); assertThat(p.seconds).isNull()
    }

    @Test
    fun `blank or number-only text is not a line`() {
        assertThat(ExerciseLineParser.parse("   ")).isNull()
        assertThat(ExerciseLineParser.parse("3x12")).isNull()
    }

    @Test
    fun `hasPrescription tells exercises from prose`() {
        assertThat(ExerciseLineParser.hasPrescription("Squats 3x12")).isTrue()
        assertThat(ExerciseLineParser.hasPrescription("Thanks for watching, see you next week")).isFalse()
    }

    // --- matching -------------------------------------------------------------------------------------------

    @Test
    fun `matches an exact library name`() {
        val m = matcher.match("Bench press")
        assertThat(m).isNotNull()
        assertThat(m!!.entry.name.lowercase()).contains("bench press")
    }

    @Test
    fun `matches plurals and case`() {
        val a = matcher.match("PUSH-UPS")
        assertThat(a!!.entry.name.lowercase()).contains("push")
        assertThat(matcher.match("lunges")).isNotNull()
    }

    @Test
    fun `matches a misspelling`() {
        val m = matcher.match("Bench pres")
        assertThat(m).isNotNull()
        assertThat(m!!.entry.name.lowercase()).contains("bench press")
    }

    @Test
    fun `applies synonyms`() {
        assertThat(ExerciseNameMatcher.tokens("DB curls")).containsExactly("dumbbell", "curl").inOrder()
        assertThat(ExerciseNameMatcher.tokens("pushups")).containsExactly("push", "up").inOrder()
    }

    @Test
    fun `a vague single word does not pick one exercise`() {
        assertThat(matcher.match("press")).isNull()
        assertThat(matcher.match("curl")).isNull()
    }

    @Test
    fun `unrelated text has no match`() {
        assertThat(matcher.match("Subscribe and hit the bell")).isNull()
        assertThat(matcher.match("")).isNull()
    }

    @Test
    fun `an exact name beats a longer containing name`() {
        val entries = listOf(ExerciseEntry("1", "Squat"), ExerciseEntry("2", "Jump squat"), ExerciseEntry("3", "Barbell back squat"))
        val m = ExerciseNameMatcher(entries)
        assertThat(m.match("squats")!!.entry.id).isEqualTo("1")
        assertThat(m.match("jump squats")!!.entry.id).isEqualTo("2")
        assertThat(m.match("barbell back squat")!!.entry.id).isEqualTo("3")
    }

    @Test
    fun `levenshtein distance`() {
        assertThat(ExerciseNameMatcher.levenshtein("kitten", "sitting")).isEqualTo(3)
        assertThat(ExerciseNameMatcher.levenshtein("", "abc")).isEqualTo(3)
        assertThat(ExerciseNameMatcher.levenshtein("same", "same")).isEqualTo(0)
    }

    // --- the draft ------------------------------------------------------------------------------------------

    private fun video(description: String, seconds: Int = 1200) =
        VideoMeta("abc", "Workout", description = description, durationSec = seconds)

    @Test
    fun `a chapter list becomes a draft with sets reps and time`() {
        val d = VideoWorkoutDrafter.draft(
            video("0:00 Intro\n0:30 Warm up\n2:00 Squats x 12\n4:00 Plank 45 sec\n5:00 Push-ups 3x10\n7:00 Thanks for watching"),
            library,
        )
        assertThat(d.source).isEqualTo(DraftSource.CHAPTERS)
        val byName = d.items.associateBy { it.text }
        val squats = byName.getValue("Squats x 12")
        assertThat(squats.kind).isEqualTo(DraftKind.MATCHED)
        assertThat(squats.reps).isEqualTo(12)
        assertThat(squats.sets).isEqualTo(VideoWorkoutDrafter.DEFAULT_SETS)
        val plank = byName.getValue("Plank 45 sec")
        assertThat(plank.seconds).isEqualTo(45)
        assertThat(plank.reps).isEqualTo(1)
        val pushups = byName.getValue("Push-ups 3x10")
        assertThat(pushups.sets).isEqualTo(3); assertThat(pushups.reps).isEqualTo(10)
        assertThat(byName.getValue("Intro").include).isFalse()
        assertThat(byName.getValue("Thanks for watching").include).isFalse()
        assertThat(byName.getValue("Warm up").kind).isEqualTo(DraftKind.UNMATCHED)
    }

    @Test
    fun `a chapter with no numbers is timed by its length`() {
        val d = VideoWorkoutDrafter.draft(video("0:00 Jumping jacks\n0:40 Burpees\n1:20 Squats\n2:00 Plank", seconds = 160), library)
        val burpees = d.items.first { it.exerciseName.lowercase().contains("burpee") }
        assertThat(burpees.seconds).isEqualTo(40)
        assertThat(burpees.sets).isEqualTo(1)
        val plank = d.items.last()
        assertThat(plank.seconds).isEqualTo(40) // 160 - 120
    }

    @Test
    fun `description lines work without chapters`() {
        val d = VideoWorkoutDrafter.draft(
            video("Today we do:\n- Squats 3x12\n- Zottman curls 3 x 8\nThanks for watching, subscribe!"),
            library,
        )
        assertThat(d.source).isEqualTo(DraftSource.DESCRIPTION)
        assertThat(d.items.map { it.text }).containsExactly("Squats 3x12", "Zottman curls 3 x 8").inOrder()
        assertThat(d.items[1].kind).isEqualTo(DraftKind.NEW)
        assertThat(d.items[1].exerciseId).isNull()
    }

    @Test
    fun `nothing useful gives an empty draft`() {
        val d = VideoWorkoutDrafter.draft(video("Hello and welcome to my channel"), library)
        assertThat(d.source).isEqualTo(DraftSource.NOTHING)
        assertThat(d.isEmpty).isTrue()
    }

    @Test
    fun `a new exercise gets a guessed muscle`() {
        assertThat(MuscleGuess.of("Zottman curl")).isEqualTo("biceps")
        assertThat(MuscleGuess.of("Glute bridge march")).isEqualTo("glutes")
        assertThat(MuscleGuess.of("Mystery move")).isEqualTo("core")
    }

    // --- links ----------------------------------------------------------------------------------------------

    @Test
    fun `reads video ids from links`() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id", "https://youtu.be/$id?t=10", "youtube.com/watch?feature=share&v=$id",
            "https://m.youtube.com/watch?v=$id", "https://www.youtube.com/shorts/$id", "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/live/$id", id, "  https://youtu.be/$id  ",
        ).forEach { assertThat(YoutubeIds.parse(it)).isEqualTo(id) }
        assertThat(YoutubeIds.parse("https://example.com/watch?v=$id")).isNull()
        assertThat(YoutubeIds.parse("not a link")).isNull()
        assertThat(YoutubeIds.parse("")).isNull()
    }

    @Test
    fun `reads ISO durations`() {
        assertThat(YoutubeIds.durationSeconds("PT1H2M3S")).isEqualTo(3723)
        assertThat(YoutubeIds.durationSeconds("PT45S")).isEqualTo(45)
        assertThat(YoutubeIds.durationSeconds("PT10M")).isEqualTo(600)
        assertThat(YoutubeIds.durationSeconds("P0D")).isEqualTo(0)
        assertThat(YoutubeIds.durationSeconds(null)).isEqualTo(0)
        assertThat(YoutubeIds.clock(3723)).isEqualTo("1:02:03")
        assertThat(YoutubeIds.clock(1710)).isEqualTo("28:30")
    }
}
