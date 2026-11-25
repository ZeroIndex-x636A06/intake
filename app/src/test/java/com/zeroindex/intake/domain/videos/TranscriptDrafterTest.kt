package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.script.Reps
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import com.zeroindex.intake.domain.training.generator.GeneratedDay
import com.zeroindex.intake.domain.training.generator.GeneratedExercise
import com.zeroindex.intake.data.training.WorkoutBuilder
import org.junit.Test

class TranscriptDrafterTest {

    private val library = ExerciseCatalog.ALL.map { ExerciseEntry(it.id, it.name) }

    private fun transcript(vararg lines: Pair<Int, String>): Transcript = Transcript(
        videoId = "dQw4w9WgXcQ",
        language = "en",
        auto = true,
        segments = lines.mapIndexed { i, (sec, text) ->
            TranscriptSegment(sec.toDouble(), (lines.getOrNull(i + 1)?.first ?: (sec + 5)).toDouble(), text)
        },
    )

    private fun DraftItem.named(name: String) = exerciseName.equals(name, ignoreCase = true)

    // --- a follow-along HIIT -----------------------------------------------------------------------------

    private val hiit = transcript(
        0 to "Welcome to the ten minute HIIT workout, no equipment needed",
        10 to "We are going to do three rounds of forty seconds on, twenty seconds off",
        25 to "First up is jumping jacks",
        65 to "Next we have squats",
        105 to "Now mountain climbers, drive those knees",
        145 to "Then push ups, keep your core tight",
        185 to "Last one is burpees, give it everything",
        225 to "Great job, that is round one",
    )

    @Test
    fun `follow-along HIIT gives the exercises in order with the circuit numbers and first-mention times`() {
        val d = TranscriptDrafter.draft(hiit, library)
        assertThat(d.source).isEqualTo(DraftSource.TRANSCRIPT)
        assertThat(d.items.map { it.exerciseName.lowercase() })
            .containsExactly("jumping jacks", "bodyweight squat", "mountain climbers", "push-up", "burpees").inOrder()
        assertThat(d.items.map { it.startSec }).containsExactly(25, 65, 105, 145, 185).inOrder()
        d.items.forEach {
            assertThat(it.sets).isEqualTo(3)
            assertThat(it.seconds).isEqualTo(40)
            assertThat(it.restSec).isEqualTo(20)
            assertThat(it.include).isTrue()
        }
        // Library exercises are matched, the rest are created on save.
        assertThat(d.items.first { it.named("push-up") }.kind).isEqualTo(DraftKind.MATCHED)
        assertThat(d.items.first { it.named("jumping jacks") }.kind).isEqualTo(DraftKind.NEW)
        assertThat(d.items.first { it.named("jumping jacks") }.exerciseId).isNull()
    }

    @Test
    fun `the length of the video is not read as an exercise time`() {
        val d = TranscriptDrafter.draft(hiit, library)
        assertThat(d.items.none { it.seconds == 600 }).isTrue()
    }

    // --- a strength explainer ------------------------------------------------------------------------------

    private val strength = transcript(
        0 to "Today we are talking about how to program strength training",
        15 to "The first lift is the barbell back squat",
        40 to "Do five sets of five reps with three minutes rest between sets",
        90 to "Second is the bench press",
        110 to "Three sets of eight reps is plenty",
        140 to "Finally the deadlift, just one set of three",
        170 to "Thanks for watching and see you next week",
    )

    @Test
    fun `strength explainer reads sets reps and rest from the spoken numbers`() {
        val d = TranscriptDrafter.draft(strength, library)
        assertThat(d.items.map { it.exerciseName }).containsExactly("Back squat", "Bench press", "Deadlift").inOrder()
        val squat = d.items[0]
        assertThat(squat.sets).isEqualTo(5)
        assertThat(squat.reps).isEqualTo(5)
        assertThat(squat.restSec).isEqualTo(180)
        assertThat(squat.seconds).isNull()
        assertThat(squat.startSec).isEqualTo(15)
        val bench = d.items[1]
        assertThat(bench.sets).isEqualTo(3)
        assertThat(bench.reps).isEqualTo(8)
        assertThat(bench.startSec).isEqualTo(90)
        val dead = d.items[2]
        assertThat(dead.sets).isEqualTo(1)
        assertThat(dead.reps).isEqualTo(3)
        assertThat(d.items.all { it.kind == DraftKind.MATCHED && it.include }).isTrue()
    }

    // --- a talky video with few exercises --------------------------------------------------------------------

    private val talky = transcript(
        0 to "So a lot of you have been asking me about squats and whether they are bad for your knees",
        20 to "They are not, and I have been coaching for fifteen years",
        45 to "The squat is a pattern, you sit down and you stand up",
        75 to "Some people will tell you to also do a plank or lunge but honestly it depends on you",
        110 to "Anyway if your squat hurts, change the depth before you change the exercise",
        150 to "That is all for today, subscribe for more",
    )

    @Test
    fun `a talky video lists what it names but only switches on what it really uses`() {
        val d = TranscriptDrafter.draft(talky, library)
        val squat = d.items.first { it.named("Bodyweight squat") }
        assertThat(squat.startSec).isEqualTo(0)
        assertThat(squat.include).isTrue()
        // Fifteen years is not a prescription for anything.
        assertThat(d.items.none { it.seconds != null }).isTrue()
        val plank = d.items.first { it.named("Plank") }
        assertThat(plank.include).isFalse()
        assertThat(plank.startSec).isEqualTo(75)
        assertThat(d.items.size).isAtMost(4)
    }

    @Test
    fun `no exercises in the captions gives nothing found`() {
        val d = TranscriptDrafter.draft(transcript(0 to "hello and welcome", 5 to "this is a video about my week", 10 to "thanks"), library)
        assertThat(d.source).isEqualTo(DraftSource.NOTHING)
        assertThat(d.items).isEmpty()
        assertThat(TranscriptDrafter.draft(Transcript("dQw4w9WgXcQ", "en", false, emptyList()), library).items).isEmpty()
    }

    // --- phrases ---------------------------------------------------------------------------------------------

    @Test
    fun `reads each side, as many as you can and a number said before the name`() {
        val t = transcript(
            0 to "Do 12 lunges on each side",
            20 to "then hold a plank for forty five seconds",
            50 to "finish with push ups, as many as you can",
        )
        val d = TranscriptDrafter.draft(t, library)
        val lunge = d.items.first { it.exerciseName.contains("lunge", ignoreCase = true) }
        assertThat(lunge.reps).isEqualTo(12)
        assertThat(lunge.eachSide).isTrue()
        val plank = d.items.first { it.named("Plank") }
        assertThat(plank.seconds).isEqualTo(45)
        assertThat(plank.reps).isEqualTo(1)
        val push = d.items.first { it.named("Push-up") }
        assertThat(push.amrap).isTrue()
        assertThat(push.eachSide).isFalse()
    }

    @Test
    fun `rest and work times are kept apart`() {
        val t = transcript(0 to "Plank for thirty seconds, then rest for fifteen seconds", 20 to "Next crunches, 20 reps")
        val d = TranscriptDrafter.draft(t, library)
        val plank = d.items.first { it.named("Plank") }
        assertThat(plank.seconds).isEqualTo(30)
        assertThat(plank.restSec).isEqualTo(15)
        val crunch = d.items.first { it.named("Crunch") }
        assertThat(crunch.reps).isEqualTo(20)
    }

    @Test
    fun `x notation and compound spoken numbers work`() {
        val t = transcript(0 to "Goblet squats 4 x 10", 10 to "and ninety seconds rest", 20 to "plank sixty seconds")
        val d = TranscriptDrafter.draft(t, library)
        val goblet = d.items.first { it.named("Goblet squat") }
        assertThat(goblet.sets).isEqualTo(4)
        assertThat(goblet.reps).isEqualTo(10)
        assertThat(goblet.restSec).isEqualTo(90)
        assertThat(d.items.first { it.named("Plank") }.seconds).isEqualTo(60)
    }

    // --- merging with chapters -------------------------------------------------------------------------------

    private val chapterMeta = VideoMeta(
        id = "dQw4w9WgXcQ",
        title = "Full body",
        description = "0:00 Intro\n0:25 Jumping jacks\n1:05 Squats\n1:45 Mountain climbers\n2:25 Push ups",
        durationSec = 240,
    )

    @Test
    fun `chapters win on timing and numbers and captions add what is missing`() {
        val offset = transcript(
            0 to "Welcome",
            27 to "First up is jumping jacks",
            68 to "Next we have squats",
            108 to "Now mountain climbers",
            148 to "Then push ups",
            190 to "Last one is burpees, for forty seconds",
        )
        val chaptersOnly = VideoWorkoutDrafter.draft(chapterMeta, library)
        val d = VideoWorkoutDrafter.draft(chapterMeta, library, offset)
        assertThat(d.source).isEqualTo(DraftSource.CHAPTERS_AND_CAPTIONS)
        // The chapter rows, times and numbers are exactly what chapters alone gave.
        val chapterRows = d.items.take(chaptersOnly.items.size)
        assertThat(chapterRows).isEqualTo(chaptersOnly.items.mapIndexed { i, it -> it.copy(key = i) })
        assertThat(chapterRows.first { it.exerciseName.contains("Jumping", ignoreCase = true) }.startSec).isEqualTo(25)
        // Burpees exist only in the captions: offered, but left off for the person to turn on.
        val burpee = d.items.first { it.named("Burpees") }
        assertThat(burpee.include).isFalse()
        assertThat(burpee.startSec).isEqualTo(190)
    }

    @Test
    fun `description lines get their time from the captions`() {
        val meta = VideoMeta(id = "dQw4w9WgXcQ", title = "Legs", description = "Squats 3 x 12\nPlank 45 sec", durationSec = 200)
        val t = transcript(0 to "hi", 30 to "start with some squats", 90 to "then the plank")
        val d = VideoWorkoutDrafter.draft(meta, library, t)
        assertThat(d.source).isEqualTo(DraftSource.DESCRIPTION_AND_CAPTIONS)
        val squat = d.items.first { it.reps == 12 }
        assertThat(squat.sets).isEqualTo(3)
        assertThat(squat.startSec).isEqualTo(30)
        val plank = d.items.first { it.seconds == 45 }
        assertThat(plank.startSec).isEqualTo(90)
    }

    @Test
    fun `with no chapters or description the captions are the draft, and no captions changes nothing`() {
        val meta = VideoMeta(id = "dQw4w9WgXcQ", title = "HIIT", description = "Subscribe!")
        val d = VideoWorkoutDrafter.draft(meta, library, hiit)
        assertThat(d.source).isEqualTo(DraftSource.TRANSCRIPT)
        assertThat(d.items).hasSize(5)
        val none = VideoWorkoutDrafter.draft(chapterMeta, library, null)
        assertThat(none).isEqualTo(VideoWorkoutDrafter.draft(chapterMeta, library))
    }

    // --- the stored time -------------------------------------------------------------------------------------

    @Test
    fun `the video time reaches the saved workout rows`() {
        val ex = GeneratedExercise(
            exerciseId = "e1", name = "Plank", muscle = "core", sets = 3, repsMin = 1, repsMax = 1, rpe = null, restSec = 20,
            progression = null, why = "", videoStartSec = 135,
        )
        val plain = ex.copy(exerciseId = "e2", videoStartSec = null)
        val day = GeneratedDay(name = "HIIT", exercises = listOf(ex, plain), estimatedMinutes = 0, why = "")
        val rows = WorkoutBuilder.fromGeneratedDay("HIIT", day, emptyMap(), now = 1L)
        assertThat(rows.exercises.map { it.videoStartSec }).containsExactly(135, null).inOrder()
        // Copying a day keeps the time.
        val copy = WorkoutBuilder.copyOfDay("Copy", rows.days.single(), rows.exercises, now = 2L)
        assertThat(copy.exercises.map { it.videoStartSec }).containsExactly(135, null).inOrder()
        assertThat(Reps.AMRAP_ENTITY_MAX).isEqualTo(99)
    }
}
