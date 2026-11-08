package com.zeroindex.intake.ui

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A snackbar raised from outside any one screen, with an optional action. */
data class AppMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (suspend () -> Unit)? = null,
)

/**
 * Messages from things that happen outside the screen the user is on - an
 * NFC tag tapped with the app closed, a background job finishing. The app
 * shell shows them as snackbars.
 */
@Singleton
class AppMessages @Inject constructor() {
    private val _events = MutableSharedFlow<AppMessage>(replay = 1, extraBufferCapacity = 4)
    val events: SharedFlow<AppMessage> = _events.asSharedFlow()

    fun post(message: AppMessage) {
        _events.tryEmit(message)
    }

    /** Clears the replay slot once shown, so it does not reappear on rotation. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consumed() = _events.resetReplayCache()
}
