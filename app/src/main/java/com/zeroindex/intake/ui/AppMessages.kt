package com.zeroindex.intake.ui

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** A snackbar raised from outside any one screen, with an optional action. */
data class AppMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (suspend () -> Unit)? = null,
)

/**
 * Messages from things that happen outside the screen the user is on - an
 * NFC tag tapped with the app closed, a background job finishing, a meal
 * template logged from a sheet. The app shell shows them as snackbars, one
 * after another.
 *
 * A queue, not a replayed shared flow: every posted message is kept until the
 * shell takes it, in order, so a message posted while another snackbar is on
 * screen (or before the shell is collecting) is not dropped.
 */
@Singleton
class AppMessages @Inject constructor() {
    private val queue = Channel<AppMessage>(Channel.UNLIMITED)
    val events: Flow<AppMessage> = queue.receiveAsFlow()

    fun post(message: AppMessage) {
        queue.trySend(message)
    }

    /** A screen to open, asked for from outside (a notification tap). Consumed by the app shell. */
    private val _openRequest = MutableStateFlow<String?>(null)
    val openRequest: StateFlow<String?> = _openRequest

    fun requestOpen(name: String) {
        _openRequest.value = name
    }

    fun openConsumed() {
        _openRequest.value = null
    }
}
