package com.zeroindex.intake.wearprotocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Amendment §2: what the phone and the watch say to each other, over the
 * Wear OS Data Layer. Both directions are DataItems rather than messages:
 * the Data Layer stores them and delivers them when the other side is next
 * reachable, so a set logged with the phone out of range is not lost - it
 * waits on the watch and goes across on reconnection.
 *
 * Weights are always kilograms on the wire (SPEC §3.5); [ActiveWorkout.weightUnit]
 * only says how to display them.
 */
object WearProtocol {
    /** Phone -> watch. The one active session, or absent. */
    const val PATH_ACTIVE = "/intake/workout/active"
    /** Watch -> phone. One item per logged set: `<prefix><clientId>`. The phone deletes it once applied. */
    const val PATH_LOGGED_PREFIX = "/intake/workout/logged/"
    const val KEY_JSON = "json"

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(workout: ActiveWorkout): String = json.encodeToString(ActiveWorkout.serializer(), workout)
    fun decodeWorkout(text: String): ActiveWorkout = json.decodeFromString(ActiveWorkout.serializer(), text)
    fun encode(set: LoggedSet): String = json.encodeToString(LoggedSet.serializer(), set)
    fun decodeSet(text: String): LoggedSet = json.decodeFromString(LoggedSet.serializer(), text)
}

@Serializable
data class WatchSet(
    /** 0-based, as on the phone. Shown as setIndex + 1. */
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val done: Boolean = false,
    val rpe: Double? = null,
)

@Serializable
data class WatchExercise(
    val exerciseId: String,
    val name: String,
    val restSec: Int,
    val sets: List<WatchSet>,
) {
    /** The first set not yet done, or null when the exercise is finished. */
    val nextSet: WatchSet? get() = sets.firstOrNull { !it.done }
}

@Serializable
data class ActiveWorkout(
    val sessionId: String,
    val title: String,
    val exercises: List<WatchExercise>,
    /** "kg" or "lb", the phone's display setting. */
    val weightUnit: String = "kg",
    val updatedAt: Long,
) {
    /** Where the watch should open: the first exercise with a set left. */
    val currentExerciseIndex: Int get() = exercises.indexOfFirst { it.nextSet != null }.let { if (it < 0) exercises.lastIndex else it }
}

@Serializable
data class LoggedSet(
    /** Generated on the watch; becomes the set's id on the phone, so a re-delivery cannot log it twice. */
    val clientId: String,
    val sessionId: String,
    val exerciseId: String,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val completedAt: Long,
    val restTakenSec: Int? = null,
)

/** RPE on a watch: half points from 6 to 10, as on the mockup. */
val RPE_CHOICES: List<Double> = (12..20).map { it / 2.0 }

/** "82.5 kg" or "182 lb", rounded the way a plate-loaded bar can be. */
fun formatWeight(kg: Double, unit: String): String {
    val value = if (unit == "lb") kg / 0.45359237 else kg
    val rounded = Math.round(value * 2) / 2.0
    val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    return "$text $unit"
}
