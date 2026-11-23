package com.zeroindex.intake.ui.quicklog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuickLogPolicyTest {
    private val allOff = QuickLogContext()

    @Test
    fun `with nothing switched on the always-available tiles show, in the drawn order`() {
        assertThat(QuickLogPolicy.visibleTiles(allOff)).containsExactly(
            QuickTile.FOOD, QuickTile.DRINK, QuickTile.SCAN, QuickTile.QUICK_CALORIES, QuickTile.MEAL_TEMPLATE,
            QuickTile.WEIGHT, QuickTile.MEASUREMENTS, QuickTile.PROGRESS_PHOTO, QuickTile.START_WORKOUT,
        ).inOrder()
    }

    @Test
    fun `jump test shows only when jump tests are switched on`() {
        assertThat(QuickLogPolicy.isVisible(QuickTile.JUMP_TEST, allOff)).isFalse()
        assertThat(QuickLogPolicy.isVisible(QuickTile.JUMP_TEST, allOff.copy(jumpTestsOn = true))).isTrue()
    }

    @Test
    fun `photo estimate needs AI on and a key, never one without the other`() {
        // aiReady is computed as master switch AND key; the policy only sees the result.
        assertThat(QuickLogPolicy.isVisible(QuickTile.PHOTO, allOff)).isFalse()
        assertThat(QuickLogPolicy.isVisible(QuickTile.PHOTO, allOff.copy(aiReady = true))).isTrue()
        assertThat(QuickLogPolicy.showsAiChip(QuickTile.PHOTO)).isTrue()
        QuickTile.entries.filter { it != QuickTile.PHOTO }.forEach { assertThat(QuickLogPolicy.showsAiChip(it)).isFalse() }
    }

    @Test
    fun `NFC tag shows only on a phone with NFC`() {
        assertThat(QuickLogPolicy.isVisible(QuickTile.NFC_TAG, allOff)).isFalse()
        assertThat(QuickLogPolicy.isVisible(QuickTile.NFC_TAG, allOff.copy(nfcAvailable = true))).isTrue()
    }

    @Test
    fun `everything on shows all twelve tiles`() {
        val all = QuickLogContext(jumpTestsOn = true, aiReady = true, nfcAvailable = true)
        assertThat(QuickLogPolicy.visibleTiles(all)).isEqualTo(QuickTile.entries)
        assertThat(QuickTile.entries).hasSize(12)
    }

    @Test
    fun `Food is the lime tile and Start workout the ink one`() {
        assertThat(QuickTile.FOOD.look).isEqualTo(QuickTileLook.Lime)
        assertThat(QuickTile.START_WORKOUT.look).isEqualTo(QuickTileLook.Ink)
        assertThat(QuickTile.entries.count { it.look == QuickTileLook.Soft }).isEqualTo(10)
    }
}
