package com.zeroindex.intake.ui.navigation

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize

/**
 * A frosted-glass backdrop for the floating pill, with no library.
 *
 * `Modifier.blur` blurs a composable itself, not what is behind it. So the content region records
 * itself once into a [GraphicsLayer] ([source]) and draws that layer as usual. The pill then draws a
 * second layer that replays the first, shifted so the part under the pill lines up, with a
 * `RenderEffect` blur on it, clipped to the pill's shape ([blurBehind]).
 *
 * Nothing here is a snapshot read during composition: positions live in plain fields and the layers
 * are replayed by the renderer, so scrolling re-records the content layer and the pill picks it up
 * with no recomposition. A [RenderEffect] needs API 31; below that [supported] is false and the pill
 * keeps its translucent fill.
 */
@Stable
class PillBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** Where the content region's top-left is, in root coordinates. */
    internal var contentOrigin: Offset = Offset.Zero

    companion object {
        val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}

@Composable
fun rememberPillBackdrop(): PillBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { PillBackdrop(layer) }
}

/** On the content region: draws it normally, and keeps a recording of it for the pill to blur. */
fun Modifier.pillBackdropSource(backdrop: PillBackdrop): Modifier =
    if (!PillBackdrop.supported) {
        this
    } else {
        this
            .onGloballyPositioned { backdrop.contentOrigin = it.positionInRoot() }
            .drawWithContent {
                backdrop.layer.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop.layer)
            }
    }

/**
 * On the pill: paints the blurred backdrop clipped to [shape]. [pillOrigin] returns the pill's top-left
 * in root coordinates, read at draw time.
 */
internal fun Modifier.pillBlurBehind(
    backdrop: PillBackdrop,
    blurLayer: GraphicsLayer,
    shape: Shape,
    radiusPx: Float,
    pillOrigin: () -> Offset,
): Modifier =
    drawBehind {
        val width = size.width.toInt()
        val height = size.height.toInt()
        if (width <= 0 || height <= 0) return@drawBehind
        val shift = pillOrigin() - backdrop.contentOrigin
        blurLayer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
        blurLayer.record(size = IntSize(width, height)) {
            translate(-shift.x, -shift.y) { drawLayer(backdrop.layer) }
        }
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
        clipPath(path) { drawLayer(blurLayer) }
    }
