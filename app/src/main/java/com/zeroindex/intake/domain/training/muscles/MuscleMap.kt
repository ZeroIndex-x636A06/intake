package com.zeroindex.intake.domain.training.muscles

import com.zeroindex.intake.domain.training.generator.Muscles
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The muscle map (exercise detail, custom exercise form, profile focus picker). Pure Kotlin: the
 * regions, how a library muscle name lights them up, and the hand-authored outlines. The Compose
 * layer only draws what is described here.
 */
enum class BodyView(val label: String) { FRONT("Front"), BACK("Back") }

/**
 * One tappable part of the body. [muscle] is the library muscle a tap stands for, so a region and
 * a library muscle name always agree (see `MuscleMapTest`).
 */
enum class MuscleRegion(val label: String, val muscle: String) {
    CHEST("Chest", "chest"),
    FRONT_DELT("Front delts", "shoulders"),
    SIDE_DELT("Side delts", "shoulders"),
    REAR_DELT("Rear delts", "shoulders"),
    BICEPS("Biceps", "biceps"),
    TRICEPS("Triceps", "triceps"),
    FOREARMS("Forearms", "forearms"),
    ABS("Abs", "core"),
    OBLIQUES("Obliques", "core"),
    LATS("Lats", "back"),
    UPPER_BACK("Upper back and traps", "back"),
    LOWER_BACK("Lower back", "lower back"),
    GLUTES("Glutes", "glutes"),
    QUADS("Quads", "quads"),
    HAMSTRINGS("Hamstrings", "hamstrings"),
    ADDUCTORS("Inner thighs", "adductors"),
    CALVES("Calves", "calves"),
}

/** Which regions are lit, strongly (primary) and lightly (secondary). */
data class MuscleHighlight(
    val primary: Set<MuscleRegion> = emptySet(),
    val secondary: Set<MuscleRegion> = emptySet(),
) {
    val isEmpty: Boolean get() = primary.isEmpty() && secondary.isEmpty()
}

object MuscleMap {

    /** The extra muscles only the custom-exercise form offers (the generator never programs them). */
    val EXTRA_MUSCLES: List<String> = listOf("lower back", "adductors")

    /** Every muscle a custom exercise can name. */
    val CUSTOM_MUSCLES: List<String> = Muscles.ALL + EXTRA_MUSCLES

    /** The regions a library muscle name covers. Unknown names cover nothing. */
    fun regionsOf(muscle: String): Set<MuscleRegion> = when (muscle.trim().lowercase()) {
        "chest" -> setOf(MuscleRegion.CHEST)
        "back" -> setOf(MuscleRegion.LATS, MuscleRegion.UPPER_BACK)
        "shoulders" -> setOf(MuscleRegion.FRONT_DELT, MuscleRegion.SIDE_DELT, MuscleRegion.REAR_DELT)
        "traps" -> setOf(MuscleRegion.UPPER_BACK)
        "biceps" -> setOf(MuscleRegion.BICEPS)
        "triceps" -> setOf(MuscleRegion.TRICEPS)
        "forearms" -> setOf(MuscleRegion.FOREARMS)
        "core" -> setOf(MuscleRegion.ABS, MuscleRegion.OBLIQUES)
        "lower back" -> setOf(MuscleRegion.LOWER_BACK)
        "glutes" -> setOf(MuscleRegion.GLUTES)
        "quads" -> setOf(MuscleRegion.QUADS)
        "hamstrings" -> setOf(MuscleRegion.HAMSTRINGS)
        "adductors" -> setOf(MuscleRegion.ADDUCTORS)
        "calves" -> setOf(MuscleRegion.CALVES)
        else -> emptySet()
    }

    /**
     * What to light for an exercise. A few names narrow a broad muscle (a lateral raise is the side
     * delt, a pulldown is the lats); everything else lights the whole muscle. A region that is
     * primary is never also drawn as secondary.
     */
    fun highlightFor(name: String, primaryMuscle: String, secondaryMuscles: List<String>): MuscleHighlight {
        val n = name.lowercase()
        val primary: Set<MuscleRegion> = when (primaryMuscle.trim().lowercase()) {
            "shoulders" -> when {
                "lateral" in n -> setOf(MuscleRegion.SIDE_DELT)
                "rear" in n || "face pull" in n || "reverse pec" in n -> setOf(MuscleRegion.REAR_DELT)
                "front raise" in n -> setOf(MuscleRegion.FRONT_DELT)
                "press" in n || "push-up" in n -> setOf(MuscleRegion.FRONT_DELT, MuscleRegion.SIDE_DELT)
                else -> regionsOf("shoulders")
            }
            "back" -> when {
                "pull-up" in n || "pulldown" in n || "chin-up" in n || "pullover" in n || "straight-arm" in n ->
                    setOf(MuscleRegion.LATS)
                "row" in n -> setOf(MuscleRegion.LATS, MuscleRegion.UPPER_BACK)
                "deadlift" in n || "good morning" in n || "clean" in n ->
                    setOf(MuscleRegion.UPPER_BACK, MuscleRegion.LOWER_BACK)
                else -> regionsOf("back")
            }
            else -> regionsOf(primaryMuscle)
        }
        val secondary = secondaryMuscles.flatMapTo(linkedSetOf()) { regionsOf(it) } - primary
        return MuscleHighlight(primary, secondary)
    }

    /** Library muscles a set of regions stands for, in map order, without repeats. */
    fun musclesOf(regions: Collection<MuscleRegion>): List<String> =
        regions.map { it.muscle }.distinct()

    /** The region under a point of the figure (figure units, 0..100 by 0..200), or null. */
    fun regionAt(view: BodyView, x: Double, y: Double): MuscleRegion? =
        outlines(view).lastOrNull { containsPoint(it.points, x, y) }?.region

    // ------------------------------------------------------------------ geometry

    const val FIGURE_WIDTH = 100.0
    const val FIGURE_HEIGHT = 200.0

    /** One closed outline of a region. [points] are corner points; draw them with [smooth]. */
    class Outline(val region: MuscleRegion, val points: List<Pt>)

    data class Pt(val x: Double, val y: Double) {
        fun mirrored() = Pt(FIGURE_WIDTH - x, y)
    }

    /** The silhouette parts, drawn first in a neutral colour. */
    fun silhouette(): List<List<Pt>> = SILHOUETTE

    fun outlines(view: BodyView): List<Outline> = if (view == BodyView.FRONT) FRONT else BACK

    /** A cubic segment: start, two control points, end. */
    class Cubic(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x3: Double, val y3: Double)

    /** Turns corner points into a closed, rounded outline (Catmull-Rom as cubic Beziers). */
    fun smooth(points: List<Pt>): List<Cubic> {
        val n = points.size
        if (n < 3) return emptyList()
        return List(n) { i ->
            val p0 = points[(i - 1 + n) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val p3 = points[(i + 2) % n]
            Cubic(
                p1.x, p1.y,
                p1.x + (p2.x - p0.x) / 6.0, p1.y + (p2.y - p0.y) / 6.0,
                p2.x - (p3.x - p1.x) / 6.0, p2.y - (p3.y - p1.y) / 6.0,
                p2.x, p2.y,
            )
        }
    }

    /** Even-odd point-in-polygon on the corner points. */
    fun containsPoint(poly: List<Pt>, x: Double, y: Double): Boolean {
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    // --- Authoring helpers. Everything below is drawn for the right half of the body (x >= 50)
    // and mirrored, so left and right always match.

    private fun pts(vararg xy: Double): List<Pt> = List(xy.size / 2) { Pt(xy[it * 2], xy[it * 2 + 1]) }

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double, n: Int = 14): List<Pt> =
        List(n) { Pt(cx + rx * cos(2 * PI * it / n), cy + ry * sin(2 * PI * it / n)) }

    /** A half outline from the top centre to the bottom centre, joined to its mirror image. */
    private fun joined(half: List<Pt>): List<Pt> = half + half.drop(1).dropLast(1).reversed().map { it.mirrored() }

    private fun both(region: MuscleRegion, half: List<Pt>): List<Outline> =
        listOf(Outline(region, half), Outline(region, half.map { it.mirrored() }.reversed()))

    private val SILHOUETTE: List<List<Pt>> = run {
        val torso = joined(
            pts(50.0, 26.0, 57.0, 27.0, 67.0, 30.0, 69.0, 37.0, 68.0, 47.0, 66.0, 60.0, 64.0, 76.0, 63.0, 92.0, 67.0, 102.0, 68.0, 110.0, 50.0, 111.0),
        )
        val arm = pts(66.0, 31.0, 74.0, 29.0, 80.0, 33.0, 82.0, 42.0, 82.0, 58.0, 83.0, 74.0, 84.0, 92.0, 85.0, 106.0, 86.0, 116.0, 82.0, 120.0, 78.0, 117.0, 76.0, 108.0, 74.0, 92.0, 72.0, 76.0, 71.0, 62.0, 70.0, 50.0, 68.0, 42.0)
        val leg = pts(51.0, 108.0, 68.0, 108.0, 69.0, 124.0, 66.0, 146.0, 65.0, 160.0, 65.0, 174.0, 62.0, 188.0, 64.0, 197.0, 52.0, 197.0, 54.0, 188.0, 54.5, 174.0, 54.0, 160.0, 54.0, 146.0, 52.0, 126.0)
        buildList {
            add(ellipse(50.0, 12.0, 7.5, 9.5))
            add(pts(45.5, 20.0, 54.5, 20.0, 56.0, 28.0, 44.0, 28.0))
            add(torso)
            add(arm); add(arm.map { it.mirrored() }.reversed())
            add(leg); add(leg.map { it.mirrored() }.reversed())
        }
    }

    private val FRONT: List<Outline> = buildList {
        addAll(both(MuscleRegion.QUADS, pts(54.0, 111.0, 61.0, 108.5, 67.0, 112.0, 66.5, 128.0, 64.0, 146.0, 59.0, 150.0, 55.5, 146.0, 54.5, 128.0)))
        addAll(both(MuscleRegion.ADDUCTORS, pts(50.8, 109.0, 53.2, 112.0, 53.8, 128.0, 55.0, 143.0, 52.5, 141.0, 50.8, 125.0)))
        addAll(both(MuscleRegion.CALVES, pts(54.5, 156.0, 61.0, 155.0, 64.0, 162.0, 63.0, 176.0, 60.0, 186.0, 56.0, 186.0, 55.0, 172.0)))
        addAll(both(MuscleRegion.CHEST, pts(51.0, 34.0, 61.0, 33.0, 67.0, 36.0, 68.0, 42.0, 65.0, 49.0, 57.0, 51.0, 51.0, 49.0)))
        addAll(both(MuscleRegion.ABS, pts(50.8, 53.0, 55.0, 53.0, 56.0, 66.0, 55.5, 82.0, 53.5, 95.0, 50.8, 98.0)))
        addAll(both(MuscleRegion.OBLIQUES, pts(57.5, 54.0, 62.5, 52.0, 64.0, 64.0, 63.0, 82.0, 59.5, 92.0, 57.0, 86.0, 57.5, 66.0)))
        addAll(both(MuscleRegion.FRONT_DELT, pts(67.0, 34.0, 73.0, 31.5, 76.0, 33.0, 76.0, 47.0, 72.0, 48.0, 68.5, 43.0)))
        addAll(both(MuscleRegion.SIDE_DELT, pts(76.5, 33.0, 80.0, 36.0, 81.5, 42.0, 80.0, 50.0, 76.5, 47.0)))
        addAll(both(MuscleRegion.BICEPS, pts(73.0, 50.0, 77.0, 49.5, 80.5, 51.0, 80.0, 68.0, 77.0, 71.0, 73.5, 68.0)))
        addAll(both(MuscleRegion.FOREARMS, pts(73.5, 76.0, 78.0, 75.0, 82.5, 78.0, 83.5, 98.0, 82.0, 108.0, 78.0, 108.0, 75.5, 98.0, 74.0, 84.0)))
    }

    private val BACK: List<Outline> = buildList {
        addAll(both(MuscleRegion.HAMSTRINGS, pts(52.0, 120.0, 58.0, 119.0, 65.0, 118.0, 66.0, 130.0, 64.0, 146.0, 59.0, 150.0, 55.0, 146.0, 53.0, 130.0)))
        addAll(both(MuscleRegion.CALVES, pts(54.5, 155.0, 61.0, 153.0, 65.0, 160.0, 64.0, 176.0, 60.0, 187.0, 56.0, 187.0, 54.5, 172.0)))
        addAll(both(MuscleRegion.GLUTES, pts(50.8, 98.0, 58.0, 96.0, 66.0, 100.0, 68.0, 108.0, 64.0, 116.0, 57.0, 118.0, 50.8, 116.0)))
        addAll(both(MuscleRegion.UPPER_BACK, pts(50.8, 25.0, 57.0, 27.0, 66.0, 32.0, 65.0, 40.0, 59.0, 52.0, 56.0, 66.0, 50.8, 68.0)))
        addAll(both(MuscleRegion.LATS, pts(60.5, 53.0, 65.5, 43.5, 69.5, 47.0, 68.5, 62.0, 63.0, 80.0, 58.0, 87.0, 57.5, 72.0, 58.0, 62.0)))
        addAll(both(MuscleRegion.LOWER_BACK, pts(50.8, 70.0, 55.5, 70.0, 57.0, 84.0, 56.0, 97.0, 50.8, 99.0)))
        addAll(both(MuscleRegion.REAR_DELT, pts(67.0, 34.0, 74.0, 31.5, 79.0, 35.0, 80.0, 44.0, 76.0, 48.0, 70.0, 43.0)))
        addAll(both(MuscleRegion.TRICEPS, pts(72.5, 47.0, 77.0, 46.5, 81.5, 49.0, 80.5, 69.0, 77.0, 72.0, 73.0, 69.0)))
        addAll(both(MuscleRegion.FOREARMS, pts(73.5, 76.0, 78.0, 75.0, 82.5, 78.0, 83.5, 98.0, 82.0, 108.0, 78.0, 108.0, 75.5, 98.0, 74.0, 84.0)))
    }

    /** Every region that appears on at least one view. */
    val DRAWN_REGIONS: Set<MuscleRegion> = (FRONT + BACK).map { it.region }.toSet()
}
