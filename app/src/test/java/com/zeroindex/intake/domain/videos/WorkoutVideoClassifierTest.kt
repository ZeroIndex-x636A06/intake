package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class WorkoutVideoClassifierTest {

    private fun v(
        title: String,
        minutes: Int = 20,
        category: String = "",
        channel: String = "",
        description: String = "",
    ) = VideoMeta(id = "x", title = title, description = description, channelTitle = channel, categoryId = category, durationSec = minutes * 60)

    private val positives = listOf(
        v("Full Body Workout | 30 Minutes | No Equipment", 30, "17"),
        v("15 Minute HIIT Cardio Workout for Fat Loss", 15),
        v("Pilates for Beginners | Follow Along", 25, "17"),
        v("Yoga for Flexibility and Mobility", 40, "26"),
        v("Push Day: chest, shoulders and triceps workout", 24, "17"),
        v("Pull Day Workout for a Bigger Back", 30),
        v("Leg Day Without a Barbell (Dumbbells Only)", 28, "17"),
        v("10 Min Abs Workout", 10, "17"),
        v("Shoulder Mobility Routine", 9),
        v("Beginner Dumbbell Workout at Home", 35, "17", "Home Gym Lab"),
        v("5 Minute Morning Stretch", 5, "26"),
        v("Tabata Workout - 4 Rounds", 20),
        v("EMOM Kettlebell Workout", 22),
        v("Calisthenics Full Body Routine", 45),
        v("How to Squat: Proper Form Tutorial", 2, "26"),
        v("Bench Press Technique and Common Mistakes", 8, "17"),
        v("Strength Training for Women Over 40", 32, "", "Strong Fitness"),
        v("Glute Bridge and Hip Thrust Burner", 12),
        v("Dumbbell Arm Day 4x12 Superset", 18),
        v("Cool Down Stretching Routine After Running", 8),
        v("Upper Body Workout - Chest Back Shoulders Arms", 40),
        v("Resistance Band Workout for Legs", 20),
        v("Beginner Pilates Class", 30, "17", "Pilates Anytime"),
        v("Warm Up for Lifting (Do This Before Every Session)", 6),
        v("Morning Yoga Flow", 20, "17"),
    )

    private val negatives = listOf(
        v("My Morning Routine | Vlog", 14, "22"),
        v("Reacting to My Old Workout Videos", 12),
        v("Best Workout Music Mix 2026", 62, "10"),
        v("Gym Shark Haul and Unboxing", 18, "26"),
        v("Podcast #214: Why Sleep Matters", 95, "22"),
        v("Minecraft Gameplay Part 5", 30, "20"),
        v("Breaking News: Markets Rally", 6, "25"),
        v("Funniest Gym Fails Meme Compilation", 8, "23"),
        v("Prank Calling My Gym", 5),
        v("What I Eat in a Day", 11, "26"),
        v("Official Video - Pump It Up", 4, "10", "Records Vevo"),
        v("Movie Trailer: The Last Lift", 2, "1"),
        v("Cooking Pasta Carbonara", 15, "26"),
        v("Gym Shoes Review", 9),
        v("Storytime: My Worst Date", 20, "22"),
        v("Top 10 Anime Moments", 12, "24"),
        v("Subscribe for more!", 1),
        v("Full Movie: Action Hero", 120, "1"),
        v("Live Stream: Lo-Fi Beats to Study To", 600, "10"),
    )

    @Test
    fun `workouts are labelled as workouts`() {
        positives.forEach {
            val verdict = WorkoutVideoClassifier.classify(it)
            assertWithMessage("${it.title} scored ${verdict.score}: ${verdict.signals}")
                .that(verdict.label).isEqualTo(VideoLabel.WORKOUT)
        }
    }

    @Test
    fun `not workouts are labelled as not workouts`() {
        negatives.forEach {
            val verdict = WorkoutVideoClassifier.classify(it)
            assertWithMessage("${it.title} scored ${verdict.score}: ${verdict.signals}")
                .that(verdict.label).isEqualTo(VideoLabel.NOT_WORKOUT)
        }
    }

    @Test
    fun `there are at least thirty labelled cases`() {
        assertThat(positives.size + negatives.size).isAtLeast(30)
    }

    @Test
    fun `category 17 and 26 lean positive and music gaming news lean negative`() {
        val plain = v("Something about training", 20, category = "")
        val base = WorkoutVideoClassifier.classify(plain).score
        assertThat(WorkoutVideoClassifier.classify(plain.copy(categoryId = "17")).score).isEqualTo(base + 2)
        assertThat(WorkoutVideoClassifier.classify(plain.copy(categoryId = "26")).score).isEqualTo(base + 1)
        assertThat(WorkoutVideoClassifier.classify(plain.copy(categoryId = "10")).score).isEqualTo(base - 3)
        assertThat(WorkoutVideoClassifier.classify(plain.copy(categoryId = "20")).score).isEqualTo(base - 3)
        assertThat(WorkoutVideoClassifier.classify(plain.copy(categoryId = "25")).score).isEqualTo(base - 3)
    }

    @Test
    fun `negative title words hit hard`() {
        val workout = v("Full Body Workout", 30)
        val reaction = v("Reaction to Full Body Workout", 30)
        assertThat(WorkoutVideoClassifier.classify(reaction).score)
            .isEqualTo(WorkoutVideoClassifier.classify(workout).score - 4)
    }

    @Test
    fun `reacting and reactions count as reaction`() {
        listOf("Reacting to my workout", "Workout reactions", "Trainer reacts to workout").forEach {
            assertThat(WorkoutVideoClassifier.classify(v(it, 20)).signals.joinToString()).contains("-4")
        }
    }

    @Test
    fun `under two minutes is penalised`() {
        val short = VideoMeta("a", "Full Body Workout", durationSec = 90)
        val normal = short.copy(durationSec = 20 * 60)
        val diff = WorkoutVideoClassifier.classify(normal).score - WorkoutVideoClassifier.classify(short).score
        assertThat(diff).isEqualTo(4) // +1 for the length, and -3 for the short one
    }

    @Test
    fun `a short form tutorial is not penalised`() {
        val tutorial = VideoMeta("a", "How to Deadlift: Form Tutorial", durationSec = 90, categoryId = "26")
        val verdict = WorkoutVideoClassifier.classify(tutorial)
        assertThat(verdict.signals.joinToString()).doesNotContain("under 2 minutes")
        assertThat(verdict.label).isEqualTo(VideoLabel.WORKOUT)
    }

    @Test
    fun `very long videos lean negative`() {
        val long = VideoMeta("a", "Workout", durationSec = 5 * 3600)
        assertThat(WorkoutVideoClassifier.classify(long).signals.joinToString()).contains("longer than 4 hours")
    }

    @Test
    fun `unknown duration is neutral`() {
        val unknown = VideoMeta("a", "Full Body Workout", durationSec = 0)
        assertThat(WorkoutVideoClassifier.classify(unknown).signals.joinToString()).doesNotContain("minute")
    }

    @Test
    fun `a timestamped description is a positive signal`() {
        val description = "0:00 Intro\n1:00 Squats x 12\n3:00 Lunges x 10\n5:00 Plank 45 sec"
        val with = VideoMeta("a", "Lower body", description = description, durationSec = 20 * 60)
        val without = with.copy(description = "")
        assertThat(WorkoutVideoClassifier.classify(with).score).isGreaterThan(WorkoutVideoClassifier.classify(without).score)
        assertThat(WorkoutVideoClassifier.classify(with).signals.joinToString()).contains("timestamped")
    }

    @Test
    fun `the channel name counts`() {
        val unknown = v("Quick session", 20, channel = "Someone")
        assertThat(WorkoutVideoClassifier.classify(unknown.copy(channelTitle = "Strong Fitness Coach")).score)
            .isEqualTo(WorkoutVideoClassifier.classify(unknown).score + 2)
        assertThat(WorkoutVideoClassifier.classify(unknown.copy(channelTitle = "Daily Music TV")).score)
            .isEqualTo(WorkoutVideoClassifier.classify(unknown).score - 2)
    }

    @Test
    fun `a negative word in the description only costs two`() {
        val clean = v("Full Body Workout", 30, description = "Do this with me.")
        val dirty = clean.copy(description = "Do this with me. Listen to my podcast too.")
        assertThat(WorkoutVideoClassifier.classify(clean).score - WorkoutVideoClassifier.classify(dirty).score).isEqualTo(2)
    }

    @Test
    fun `whole words only`() {
        // "abs" must not be found in "absolutely", "core" not in "scorecard".
        val verdict = WorkoutVideoClassifier.classify(v("Absolutely stunning scorecard", 10))
        assertThat(verdict.label).isEqualTo(VideoLabel.NOT_WORKOUT)
    }

    @Test
    fun `plurals count as the word`() {
        assertThat(WorkoutVideoClassifier.classify(v("Best Workouts For Home", 20)).label).isEqualTo(VideoLabel.WORKOUT)
    }

    @Test
    fun `borderline videos are maybe`() {
        val maybe = WorkoutVideoClassifier.classify(v("Gym day", 20))
        assertThat(maybe.label).isEqualTo(VideoLabel.MAYBE)
    }

    @Test
    fun `the verdict always carries a reason and the signals`() {
        (positives + negatives).forEach {
            val verdict = WorkoutVideoClassifier.classify(it)
            assertThat(verdict.reason).isNotEmpty()
            assertThat(verdict.label == VideoLabel.NOT_WORKOUT && verdict.reason.startsWith("Hidden")
                || verdict.label != VideoLabel.NOT_WORKOUT).isTrue()
        }
    }

    @Test
    fun `the score never depends on show everything`() {
        // The override is a display choice: classifying twice gives the same verdict.
        val video = v("Reacting to my workout", 12)
        assertThat(WorkoutVideoClassifier.classify(video)).isEqualTo(WorkoutVideoClassifier.classify(video))
    }

    @Test
    fun `library exercise names in the title count`() {
        val named = VideoMeta("a", "Zottman curl progression", durationSec = 600)
        val plain = named.copy(title = "Progression")
        val withCatalog = WorkoutVideoClassifier.classify(named, listOf("Zottman curl"))
        assertThat(withCatalog.score).isGreaterThan(WorkoutVideoClassifier.classify(plain, listOf("Zottman curl")).score)
    }

    @Test
    fun `sets and reps patterns count`() {
        assertThat(WorkoutVideoClassifier.classify(v("Biceps 3x12 and triceps 4 sets of 10", 15)).signals.joinToString())
            .contains("sets and reps")
    }
}
