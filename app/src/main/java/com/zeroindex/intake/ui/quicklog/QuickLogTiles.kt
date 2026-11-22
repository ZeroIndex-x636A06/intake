package com.zeroindex.intake.ui.quicklog

import androidx.annotation.StringRes
import com.zeroindex.intake.R

/** The tiles of the Quick log sheet, in the order the mockup draws them (3 columns). */
enum class QuickTile(@StringRes val labelRes: Int, val look: QuickTileLook = QuickTileLook.Soft) {
    FOOD(R.string.quick_tile_food, QuickTileLook.Lime),
    DRINK(R.string.quick_tile_drink),
    SCAN(R.string.quick_tile_scan),
    PHOTO(R.string.quick_tile_photo),
    QUICK_CALORIES(R.string.quick_tile_quick_calories),
    MEAL_TEMPLATE(R.string.quick_tile_meal_template),
    WEIGHT(R.string.quick_tile_weight),
    MEASUREMENTS(R.string.quick_tile_measurements),
    PROGRESS_PHOTO(R.string.quick_tile_progress_photo),
    START_WORKOUT(R.string.quick_tile_start_workout, QuickTileLook.Ink),
    JUMP_TEST(R.string.quick_tile_jump_test),
    NFC_TAG(R.string.quick_tile_nfc_tag),
}

enum class QuickTileLook { Soft, Lime, Ink }

/**
 * What decides which tiles show. Pure data so the rule can be unit tested.
 *
 * - [jumpTestsOn]: Settings > Training > "Show jump tests in Insights".
 * - [aiReady]: the AI master switch is on AND an OpenRouter key is saved. The photo estimate is the one tile that needs it.
 * - [nfcAvailable]: this phone has NFC hardware. Tags are written from a meal template, so there is no separate switch.
 */
data class QuickLogContext(
    val jumpTestsOn: Boolean = false,
    val aiReady: Boolean = false,
    val nfcAvailable: Boolean = false,
)

object QuickLogPolicy {
    /** A tile whose feature is switched off, or needs a key that is not there, is hidden, never greyed out. */
    fun isVisible(tile: QuickTile, context: QuickLogContext): Boolean = when (tile) {
        QuickTile.PHOTO -> context.aiReady
        QuickTile.JUMP_TEST -> context.jumpTestsOn
        QuickTile.NFC_TAG -> context.nfcAvailable
        else -> true
    }

    fun visibleTiles(context: QuickLogContext): List<QuickTile> =
        QuickTile.entries.filter { isVisible(it, context) }

    /** Only the AI tile carries the small AI chip. */
    fun showsAiChip(tile: QuickTile): Boolean = tile == QuickTile.PHOTO

    /** How many one-tap water sizes the sheet offers: the saved sizes, at most four, from Settings. */
    const val MAX_WATER_PILLS = 4
}
