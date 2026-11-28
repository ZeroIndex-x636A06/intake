package com.zeroindex.intake.data.training

import android.content.Context
import com.zeroindex.intake.domain.training.WorkoutPause
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The pause state of the running workout. It lives in this phone's preferences, not in the database (no schema change),
 * so a pause survives leaving the app or the process being killed. It holds one workout at a time: starting a pause for a
 * different session replaces the old one. Only the net trained time is saved with a finished session
 * (TrainingRepository.finishSession).
 */
/** The pause and reopen-gap totals handed to Finish. */
data class FinishedPause(val pausedMs: Long, val reopenGapMs: Long, val reopenedAt: Long?)

@Singleton
class WorkoutPauseStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = runCatching { context.getSharedPreferences("workout_pause", Context.MODE_PRIVATE) }.getOrNull()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<WorkoutPause?> = _state.asStateFlow()

    /** The pause record for [sessionId], or null when that workout has none. */
    fun forSession(sessionId: String): WorkoutPause? = _state.value?.takeIf { it.sessionId == sessionId }

    fun pause(sessionId: String, now: Long) {
        val current = forSession(sessionId) ?: WorkoutPause(sessionId)
        set(current.pause(now))
    }

    fun resume(sessionId: String, now: Long) {
        val current = forSession(sessionId) ?: return
        set(current.resume(now))
    }

    /** Reopen: remembers the idle time since Finish, which the clock and the next Finish leave out (see WorkoutPause.reopenGapMs). */
    fun reopen(sessionId: String, gapMs: Long, now: Long) {
        val current = forSession(sessionId) ?: WorkoutPause(sessionId)
        set(current.copy(reopenGapMs = gapMs.coerceAtLeast(0L), reopenedAt = now))
    }

    /** What Finish needs to know about [sessionId] at [now], then forgets it (the workout is over). */
    fun finish(sessionId: String, now: Long): FinishedPause {
        val p = forSession(sessionId)
        val out = FinishedPause(p?.totalPausedMs(now) ?: 0L, p?.reopenGapMs ?: 0L, p?.reopenedAt)
        clear(sessionId)
        return out
    }

    fun clear(sessionId: String) {
        if (_state.value?.sessionId == sessionId) set(null)
    }

    private fun set(value: WorkoutPause?) {
        _state.value = value
        runCatching {
            prefs?.edit()?.apply {
                if (value == null) clear() else {
                    putString(KEY_SESSION, value.sessionId)
                    putLong(KEY_PAUSED_MS, value.pausedMs)
                    if (value.pausedSince != null) putLong(KEY_SINCE, value.pausedSince) else remove(KEY_SINCE)
                    putLong(KEY_GAP, value.reopenGapMs)
                    if (value.reopenedAt != null) putLong(KEY_REOPENED, value.reopenedAt) else remove(KEY_REOPENED)
                }
            }?.apply()
        }
    }

    private fun load(): WorkoutPause? = runCatching {
        val p = prefs ?: return@runCatching null
        val id = p.getString(KEY_SESSION, null) ?: return@runCatching null
        WorkoutPause(id, p.getLong(KEY_PAUSED_MS, 0L), if (p.contains(KEY_SINCE)) p.getLong(KEY_SINCE, 0L) else null,
            p.getLong(KEY_GAP, 0L), if (p.contains(KEY_REOPENED)) p.getLong(KEY_REOPENED, 0L) else null)
    }.getOrNull()

    private companion object {
        const val KEY_GAP = "reopen_gap"
        const val KEY_REOPENED = "reopened_at"
        const val KEY_SESSION = "session"
        const val KEY_PAUSED_MS = "paused_ms"
        const val KEY_SINCE = "paused_since"
    }
}
