package com.zeroindex.intake.domain.onboarding

/** A rectangle in pixels, free of Android and Compose types so the placement can be unit-tested. */
data class PxRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class PxPoint(val x: Float, val y: Float)

/**
 * Where the coach-mark bubble goes relative to the highlighted target.
 *
 * - A tall, narrow target (the vertical pill on a wide screen) gets the bubble beside it.
 * - Otherwise the bubble goes below the target, or above when there is no room below.
 * - If the target fills the screen, the bubble sits at the side with more room, over the target.
 * It is always clamped inside the screen and the system bars.
 */
object BubblePlacement {

    fun place(
        target: PxRect,
        screenWidth: Float,
        screenHeight: Float,
        bubbleWidth: Float,
        bubbleHeight: Float,
        gap: Float,
        margin: Float,
        topInset: Float = 0f,
        bottomInset: Float = 0f,
    ): PxPoint {
        val minX = margin
        val maxX = (screenWidth - bubbleWidth - margin).coerceAtLeast(minX)
        val minY = topInset + margin
        val maxY = (screenHeight - bottomInset - bubbleHeight - margin).coerceAtLeast(minY)

        val tallAndNarrow = target.height > target.width * 2f
        if (tallAndNarrow) {
            val roomRight = screenWidth - target.right
            val x = if (roomRight >= bubbleWidth + gap + margin) target.right + gap else target.left - gap - bubbleWidth
            return PxPoint(x.coerceIn(minX, maxX), (target.centerY - bubbleHeight / 2f).coerceIn(minY, maxY))
        }

        val x = (target.centerX - bubbleWidth / 2f).coerceIn(minX, maxX)
        val roomBelow = (screenHeight - bottomInset) - target.bottom
        val roomAbove = target.top - topInset
        val need = bubbleHeight + gap + margin
        val y = when {
            roomBelow >= need -> target.bottom + gap
            roomAbove >= need -> target.top - gap - bubbleHeight
            roomBelow >= roomAbove -> maxY
            else -> minY
        }
        return PxPoint(x, y.coerceIn(minY, maxY))
    }
}
