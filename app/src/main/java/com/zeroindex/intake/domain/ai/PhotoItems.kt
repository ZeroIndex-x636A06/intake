package com.zeroindex.intake.domain.ai

/**
 * One food found in a photo, as the review screen holds it. [line] is at 1x (before the "scale all portions"
 * slider); [id] is the item's position in the model's answer, so edits, removals and allergen flags stay tied to
 * the right food however the list is changed.
 */
data class PhotoItem(
    val id: Int,
    val line: EstimateLine,
    /** The model's plausible portion span in grams, when it gave one. */
    val gramsLow: Double? = null,
    val gramsHigh: Double? = null,
    val confidence: String = "medium",
    /** Unticked items are left out of the total and are not logged. */
    val included: Boolean = true,
)

/**
 * The review screen's state changes, pure so they are tested rather than trusted. A photo returns a list of
 * foods; the person unticks, renames, resizes and then logs every ticked one in a single tap, each as its own
 * food log entry.
 */
object PhotoItems {

    fun of(items: List<PhotoItemInput>): List<PhotoItem> = items.mapIndexed { i, it ->
        PhotoItem(i, it.line, it.gramsLow, it.gramsHigh, it.confidence)
    }

    /** The ticked items at the current scale: what is totalled and what gets logged. */
    fun shown(items: List<PhotoItem>, scale: Double): List<PhotoItem> =
        items.filter { it.included }.map { it.copy(line = it.line.scaled(scale)) }

    fun total(items: List<PhotoItem>, scale: Double): EstimateLine = VisionMath.total(shown(items, scale).map { it.line })

    fun toggle(items: List<PhotoItem>, id: Int): List<PhotoItem> =
        items.map { if (it.id == id) it.copy(included = !it.included) else it }

    fun rename(items: List<PhotoItem>, id: Int, name: String): List<PhotoItem> =
        items.map { if (it.id == id) it.copy(line = it.line.copy(name = name)) else it }

    /**
     * Sets the portion of one item (given as it is displayed, at the current [scale]) and rescales its macros
     * with it, as the old gram field did. The model's range scales along so it stays a range around the value.
     */
    fun setGrams(items: List<PhotoItem>, id: Int, shownGrams: Double, scale: Double): List<PhotoItem> =
        items.map { item ->
            if (item.id != id) return@map item
            val baseGrams = shownGrams / scale
            val factor = if (item.line.grams > 0) baseGrams / item.line.grams else 1.0
            item.copy(
                line = item.line.scaled(factor).copy(name = item.line.name, grams = baseGrams),
                gramsLow = item.gramsLow?.times(factor),
                gramsHigh = item.gramsHigh?.times(factor),
            )
        }

    /** Sets one macro of one item (given as displayed, at the current [scale]). */
    fun setMacro(items: List<PhotoItem>, id: Int, scale: Double, change: (EstimateLine) -> EstimateLine): List<PhotoItem> =
        items.map { item ->
            if (item.id != id) return@map item
            val shown = change(item.line.scaled(scale))
            item.copy(line = shown.scaled(1.0 / scale).copy(name = item.line.name))
        }

    /** Items worth logging: ticked, and with some calories or protein. */
    fun loggable(shown: List<PhotoItem>): List<PhotoItem> = shown.filter { it.line.kcal > 0 || it.line.protein > 0 }
}

/** What the model returned for one food, before it becomes a [PhotoItem]. */
data class PhotoItemInput(
    val line: EstimateLine,
    val gramsLow: Double?,
    val gramsHigh: Double?,
    val confidence: String,
)
