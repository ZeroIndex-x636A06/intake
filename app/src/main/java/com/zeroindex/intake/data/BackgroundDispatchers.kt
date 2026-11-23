package com.zeroindex.intake.data

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Single-thread dispatchers at background priority, for work the screen can wait for: the allergen scan of result
 * rows, the offline food search and the per-recipe preparation behind Log food.
 *
 * `Dispatchers.Default` threads run at the same priority as the main thread, so a long list scan on a phone with few
 * free cores (or the software-rendered emulator) took the CPU from the frame that was meant to show it. Background
 * threads give way to the main thread; rows not scanned yet keep showing "Checking..." until their verdict arrives
 * (a verdict is never assumed). One thread each, so a list of rows is scanned one after another, not in parallel.
 */
object BackgroundDispatchers {
    /** The allergen scan of food rows (Log food, Drinks, menus). */
    val scan: CoroutineDispatcher by lazy { create("intake-scan") }

    /** Saved recipes: calorie estimate and allergen chip for each, once per change. */
    val recipes: CoroutineDispatcher by lazy { create("intake-recipes") }

    /** The offline USDA database search and ranking behind the search field. */
    val lookup: CoroutineDispatcher by lazy { create("intake-lookup") }

    private fun create(name: String): CoroutineDispatcher {
        val count = AtomicInteger()
        return Executors.newSingleThreadExecutor { task ->
            Thread(
                {
                    // Unit tests run on a plain JVM, where this is a no-op.
                    runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
                    task.run()
                },
                "$name-${count.incrementAndGet()}",
            ).apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }
}
