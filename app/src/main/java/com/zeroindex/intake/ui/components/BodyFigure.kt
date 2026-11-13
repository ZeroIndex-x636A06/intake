package com.zeroindex.intake.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.zeroindex.intake.domain.training.muscles.BodyView
import com.zeroindex.intake.domain.training.muscles.MuscleMap
import com.zeroindex.intake.domain.training.muscles.MuscleRegion

/** The front and back figure side by side, shared by every muscle map in the app (units are figure units). */
internal const val FIGURE_GAP = 16.0
internal const val FIGURE_TOTAL_WIDTH = MuscleMap.FIGURE_WIDTH * 2 + FIGURE_GAP

private fun closedPath(points: List<MuscleMap.Pt>): Path = Path().apply {
    val segments = MuscleMap.smooth(points)
    if (segments.isEmpty()) return@apply
    moveTo(segments[0].x0.toFloat(), segments[0].y0.toFloat())
    segments.forEach { c -> cubicTo(c.x1.toFloat(), c.y1.toFloat(), c.x2.toFloat(), c.y2.toFloat(), c.x3.toFloat(), c.y3.toFloat()) }
    close()
}

private fun openPath(points: List<MuscleMap.Pt>): Path = Path().apply {
    val segments = MuscleMap.smoothOpen(points)
    if (segments.isEmpty()) return@apply
    moveTo(segments[0].x0.toFloat(), segments[0].y0.toFloat())
    segments.forEach { c -> cubicTo(c.x1.toFloat(), c.y1.toFloat(), c.x2.toFloat(), c.y2.toFloat(), c.x3.toFloat(), c.y3.toFloat()) }
}

/** The paths of the figure, built once per composition. */
internal class FigurePaths {
    val silhouette: List<Path> = MuscleMap.silhouette().map(::closedPath)
    val front: List<Pair<MuscleRegion, Path>> = MuscleMap.outlines(BodyView.FRONT).map { it.region to closedPath(it.points) }
    val back: List<Pair<MuscleRegion, Path>> = MuscleMap.outlines(BodyView.BACK).map { it.region to closedPath(it.points) }
    val frontLines: List<Path> = MuscleMap.lines(BodyView.FRONT).map(::openPath)
    val backLines: List<Path> = MuscleMap.lines(BodyView.BACK).map(::openPath)
}

internal class FigureColors(
    val body: Color,
    val bodyEdge: Color,
    val edge: Color,
    val line: Color,
    val selected: Color,
)

/** Draws both views into the scope's full width. [fill] gives each region its colour; [selected] gets a heavy outline. */
internal fun DrawScope.drawFigure(
    paths: FigurePaths,
    colors: FigureColors,
    selected: MuscleRegion?,
    fill: (MuscleRegion) -> Color,
) {
    val s = (size.width / FIGURE_TOTAL_WIDTH).toFloat()
    fun view(xOffset: Double, regions: List<Pair<MuscleRegion, Path>>, lines: List<Path>) {
        withTransform({
            translate(left = (xOffset * s).toFloat(), top = 0f)
            scale(s, s, pivot = Offset.Zero)
        }) {
            paths.silhouette.forEach {
                drawPath(it, color = colors.body)
                drawPath(it, color = colors.bodyEdge, style = Stroke(width = 0.5f, join = StrokeJoin.Round))
            }
            regions.forEach { (region, path) ->
                drawPath(path, color = fill(region))
                drawPath(path, color = colors.edge, style = Stroke(width = 0.45f, join = StrokeJoin.Round))
            }
            lines.forEach { drawPath(it, color = colors.line, style = Stroke(width = 0.35f, cap = StrokeCap.Round)) }
            if (selected != null) {
                regions.filter { it.first == selected }.forEach { (_, path) ->
                    drawPath(path, color = colors.selected, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
                }
            }
        }
    }
    view(0.0, paths.front, paths.frontLines)
    view(MuscleMap.FIGURE_WIDTH + FIGURE_GAP, paths.back, paths.backLines)
}

/** The region under a tap at ([x], [y]) on a canvas of [width] pixels, or null. */
internal fun figureRegionAt(width: Float, x: Float, y: Float): MuscleRegion? {
    val s = width / FIGURE_TOTAL_WIDTH
    val ux = x / s
    val uy = y / s
    return when {
        ux <= MuscleMap.FIGURE_WIDTH -> MuscleMap.regionAt(BodyView.FRONT, ux, uy)
        ux >= MuscleMap.FIGURE_WIDTH + FIGURE_GAP -> MuscleMap.regionAt(BodyView.BACK, ux - MuscleMap.FIGURE_WIDTH - FIGURE_GAP, uy)
        else -> null
    }
}
