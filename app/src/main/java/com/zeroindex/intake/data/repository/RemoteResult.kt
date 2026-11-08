package com.zeroindex.intake.data.repository

/**
 * SPEC §3.1: the three network paths degrade to manual entry with a clear
 * message, never a crash and never a blank screen. Callers get one of these
 * instead of an exception, so every failure has somewhere sensible to land.
 */
sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>

    /** No API key configured. The UI offers settings, then manual entry. */
    data object MissingKey : RemoteResult<Nothing>

    /** Reached the service, but it had nothing. */
    data object NotFound : RemoteResult<Nothing>

    /** Offline, timed out, rate limited, or the service returned nonsense. */
    data class Failed(val message: String) : RemoteResult<Nothing>
}
