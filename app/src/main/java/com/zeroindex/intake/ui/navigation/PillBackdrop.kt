package com.zeroindex.intake.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Kept so the shell compiles unchanged; it does nothing. The dock used to be frosted glass: the content region recorded itself
 * into a graphics layer and the pill drew a blurred copy behind its translucent fill. The redesign (DESIGN-V6 a.5) has no blur
 * and no glass anywhere, so the dock is the solid `dock` colour and there is nothing to record. Phase 1b deletes this type with
 * its two call sites in IntakeApp.
 */
@Stable
class PillBackdrop internal constructor() {
    companion object {
        /** No blur is drawn on any API level. */
        val supported: Boolean get() = false
    }
}

@Composable
fun rememberPillBackdrop(): PillBackdrop = remember { PillBackdrop() }

/** A no-op: the content region is drawn as it is. */
@Suppress("UnusedReceiverParameter")
fun Modifier.pillBackdropSource(backdrop: PillBackdrop): Modifier = this
