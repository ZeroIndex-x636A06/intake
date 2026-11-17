package com.zeroindex.intake.ui

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AppMessagesTest {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun `the bus delivers the action and performing it runs the undo`() = runBlocking {
        val bus = AppMessages(appScope)
        var undone = 0
        bus.post(AppMessage("Logged", "Undo") { undone++ })
        val received = bus.events.first()
        assertThat(received.actionLabel).isEqualTo("Undo")
        assertThat(received.onAction).isNotNull()
        bus.perform(received)!!.join()
        assertThat(undone).isEqualTo(1)
    }

    @Test
    fun `messages posted before anyone collects are kept in order`() = runBlocking {
        val bus = AppMessages(appScope)
        bus.post(AppMessage("one"))
        bus.post(AppMessage("two"))
        assertThat(bus.events.first().text).isEqualTo("one")
        assertThat(bus.events.first().text).isEqualTo("two")
    }

    @Test
    fun `a message without an action performs nothing`() {
        assertThat(AppMessages(appScope).perform(AppMessage("Saved"))).isNull()
    }

    @Test
    fun `the undo still runs when the screen that posted it is cancelled`() = runBlocking {
        val bus = AppMessages(appScope)
        var undone = false
        val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        screenScope.launch { bus.post(AppMessage("Deleted", "Undo") { delay(50); undone = true }) }.join()
        val job = bus.perform(bus.events.first())!!
        screenScope.cancel() // the screen is gone before the action finishes
        job.join()
        assertThat(undone).isTrue()
    }

    @Test
    fun `an undo that throws does not break later undos`() = runBlocking {
        val bus = AppMessages(appScope)
        bus.perform(AppMessage("x", "Undo") { error("boom") })!!.join()
        var ran = false
        bus.perform(AppMessage("y", "Undo") { ran = true })!!.join()
        assertThat(ran).isTrue()
    }
}
