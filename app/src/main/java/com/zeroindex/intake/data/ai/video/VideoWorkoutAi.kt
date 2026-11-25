package com.zeroindex.intake.data.ai.video

import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.S
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.videos.Chapter
import com.zeroindex.intake.domain.videos.Transcript
import com.zeroindex.intake.domain.videos.VideoMeta
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AiWorkoutExercise(
    val name: String,
    val sets: Int,
    /** Reps per set, or 0 when the exercise is timed. */
    val reps: Int,
    /** Work seconds when timed, else null. */
    val seconds: Int? = null,
    val restSec: Int? = null,
    /** Second of the video where this exercise is first shown or named, when the transcript says; else null. */
    val startSec: Double? = null,
)

@Serializable
data class AiVideoWorkout(val exercises: List<AiWorkoutExercise>)

/**
 * "Improve with AI" on Make this a workout. It runs only when the person taps that button, through the AI gateway (so the
 * master switch, key, privacy setting, budget cap and usage meter all apply). It sends the title, the chapters, the
 * description and, when the person fetched or pasted one, the transcript with its times (all trimmed), nothing else. The reply must fit a strict schema (one repair attempt, then a plain error) and is only
 * ever a draft the person reviews; nothing is saved from it unseen.
 */
@Singleton
class VideoWorkoutAi @Inject constructor(private val gateway: AiGateway) {

    fun isAvailable(): Boolean = gateway.isConfigured()

    suspend fun improve(video: VideoMeta, chapters: List<Chapter>, transcript: Transcript? = null): List<AiWorkoutExercise> {
        val chapterText = chapters.joinToString("\n") { "${it.startSec}s ${it.title}" }
        val transcriptText = transcript?.takeIf { !it.isEmpty }?.timedText(MAX_TRANSCRIPT_CHARS)
        val outcome = gateway.structured(
            feature = AiFeature.VIDEO_TO_WORKOUT,
            trigger = AiTrigger.USER_ACTION,
            serializer = AiVideoWorkout.serializer(),
            schema = schema,
            system = SYSTEM,
            parts = listOf(
                AiPart.Text(
                    "Title: ${video.title.take(200)}\nLength: ${video.durationSec} seconds\n\n" +
                        "Chapters:\n${chapterText.take(3_000)}\n\nDescription:\n${video.description.take(descriptionBudget(transcriptText))}" +
                        (transcriptText?.let { "\n\nTranscript (each line starts with its time, m:ss):\n$it" } ?: ""),
                ),
            ),
        )
        return outcome.value.exercises.filter { it.name.isNotBlank() }.take(40)
    }

    private companion object {
        /** About 3,000 tokens of captions; a longer transcript is thinned to an even spread of whole lines (see `TranscriptTrim`). */
        const val MAX_TRANSCRIPT_CHARS = 12_000

        /** With captions the description gets a smaller share, so one request stays around 5,000 tokens. */
        fun descriptionBudget(transcriptText: String?) = if (transcriptText == null) 8_000 else 4_000

        val schema: JsonObject = S.obj(
            "exercises" to S.array(
                S.obj(
                    "name" to S.string("The exercise name, as plain as possible (Squat, Push-up, Plank)"),
                    "sets" to S.integer("Number of sets, at least 1"),
                    "reps" to S.integer("Reps per set; 0 when the exercise is timed"),
                    "seconds" to S.nullableNumber("Work time in seconds when timed, otherwise null"),
                    "restSec" to S.nullableNumber("Rest in seconds between sets, or null"),
                    "startSec" to S.nullableNumber("Second of the video where this exercise is first named or shown, from the transcript times, otherwise null"),
                ),
            ),
        )

        const val SYSTEM = "You turn the text of one workout video (title, chapters, description, and optionally a timed transcript) into a list of exercises with " +
            "sets, reps or timed work and rest. Use only what the text says or clearly implies; never invent exercises. " +
            "Skip intros, outros, thanks and sponsor segments. Keep the order of the video. When a transcript with times is given, set startSec to the second where each exercise is first named or shown; chapters, when present, give the exact time and win. If the text has no workout, return an " +
            "empty exercises array. Reply with JSON only."
    }
}
