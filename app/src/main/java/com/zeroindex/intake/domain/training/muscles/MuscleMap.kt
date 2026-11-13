package com.zeroindex.intake.domain.training.muscles

import com.zeroindex.intake.domain.training.generator.Muscles

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
enum class MuscleRegion(val label: String, val muscle: String, val body: BodyRegion) {
    CHEST_UPPER("Upper chest", "chest", BodyRegion.CHEST),
    CHEST_MID_LOWER("Mid and lower chest", "chest", BodyRegion.CHEST),
    LATS("Lats", "back", BodyRegion.BACK),
    MID_BACK("Mid back", "back", BodyRegion.BACK),
    UPPER_TRAPS("Upper traps", "traps", BodyRegion.BACK),
    LOWER_BACK("Lower back", "lower back", BodyRegion.BACK),
    FRONT_DELT("Front delts", "shoulders", BodyRegion.SHOULDERS),
    SIDE_DELT("Side delts", "shoulders", BodyRegion.SHOULDERS),
    REAR_DELT("Rear delts", "shoulders", BodyRegion.SHOULDERS),
    BICEPS("Biceps", "biceps", BodyRegion.ARMS),
    TRICEPS("Triceps", "triceps", BodyRegion.ARMS),
    FOREARMS("Forearms", "forearms", BodyRegion.ARMS),
    QUADS("Quads", "quads", BodyRegion.LEGS),
    HAMSTRINGS("Hamstrings", "hamstrings", BodyRegion.LEGS),
    GLUTES("Glutes", "glutes", BodyRegion.LEGS),
    HIP_ABDUCTORS("Hip abductors", "abductors", BodyRegion.LEGS),
    HIP_ADDUCTORS("Inner thighs", "adductors", BodyRegion.LEGS),
    CALVES("Calves", "calves", BodyRegion.LEGS),
    ABS("Abs", "core", BodyRegion.CORE),
    OBLIQUES("Obliques", "core", BodyRegion.CORE),
}

/**
 * The six coarse regions the map shows first (docs/research/LIFTOSAUR-NOTES.md section 6.2: about 20
 * trackable groups under 6 body regions). The 20 [MuscleRegion]s are the detail behind them.
 */
enum class BodyRegion(val label: String) {
    CHEST("Chest"), BACK("Back"), SHOULDERS("Shoulders"), ARMS("Arms"), LEGS("Legs"), CORE("Core"),
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
    val EXTRA_MUSCLES: List<String> = listOf("lower back", "adductors", "abductors")

    /** Every muscle a custom exercise can name. */
    val CUSTOM_MUSCLES: List<String> = Muscles.ALL + EXTRA_MUSCLES

    /** The regions a library muscle name covers. Unknown names cover nothing. */
    fun regionsOf(muscle: String): Set<MuscleRegion> = when (muscle.trim().lowercase()) {
        "chest" -> setOf(MuscleRegion.CHEST_UPPER, MuscleRegion.CHEST_MID_LOWER)
        "back" -> setOf(MuscleRegion.LATS, MuscleRegion.MID_BACK)
        "shoulders" -> setOf(MuscleRegion.FRONT_DELT, MuscleRegion.SIDE_DELT, MuscleRegion.REAR_DELT)
        "traps" -> setOf(MuscleRegion.UPPER_TRAPS)
        "biceps" -> setOf(MuscleRegion.BICEPS)
        "triceps" -> setOf(MuscleRegion.TRICEPS)
        "forearms" -> setOf(MuscleRegion.FOREARMS)
        "core" -> setOf(MuscleRegion.ABS, MuscleRegion.OBLIQUES)
        "lower back" -> setOf(MuscleRegion.LOWER_BACK)
        "glutes" -> setOf(MuscleRegion.GLUTES)
        "quads" -> setOf(MuscleRegion.QUADS)
        "hamstrings" -> setOf(MuscleRegion.HAMSTRINGS)
        "adductors" -> setOf(MuscleRegion.HIP_ADDUCTORS)
        "abductors" -> setOf(MuscleRegion.HIP_ABDUCTORS)
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
            "chest" -> when {
                "incline" in n -> setOf(MuscleRegion.CHEST_UPPER)
                "dip" in n || "decline" in n -> setOf(MuscleRegion.CHEST_MID_LOWER)
                else -> regionsOf("chest")
            }
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
                "row" in n -> setOf(MuscleRegion.LATS, MuscleRegion.MID_BACK)
                "deadlift" in n || "good morning" in n || "clean" in n ->
                    setOf(MuscleRegion.MID_BACK, MuscleRegion.LOWER_BACK)
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

    // The figure itself is generated data (tools/musclemap/build_musclemap.py -> MuscleGeometry.kt): an original
    // anatomical-style front and back drawing, both sides listed.
    private val SILHOUETTE: List<List<Pt>> = MuscleGeometry.SILHOUETTE
    private val FRONT: List<Outline> = MuscleGeometry.FRONT
    private val BACK: List<Outline> = MuscleGeometry.BACK

    /** Thin anatomy lines (collarbone, ab lines, kneecap): open strokes drawn over the regions, to be smoothed with [smoothOpen]. */
    fun lines(view: BodyView): List<List<Pt>> = if (view == BodyView.FRONT) MuscleGeometry.FRONT_LINES else MuscleGeometry.BACK_LINES

    /** Turns corner points into an open, rounded stroke (the end points are repeated, so it passes through all of them). */
    fun smoothOpen(points: List<Pt>): List<Cubic> {
        val n = points.size
        if (n < 2) return emptyList()
        val ext = listOf(points.first()) + points + listOf(points.last())
        return List(n - 1) { i ->
            val p0 = ext[i]
            val p1 = ext[i + 1]
            val p2 = ext[i + 2]
            val p3 = ext[i + 3]
            Cubic(
                p1.x, p1.y,
                p1.x + (p2.x - p0.x) / 6.0, p1.y + (p2.y - p0.y) / 6.0,
                p2.x - (p3.x - p1.x) / 6.0, p2.y - (p3.y - p1.y) / 6.0,
                p2.x, p2.y,
            )
        }
    }

    /** Every region that appears on at least one view. */
    val DRAWN_REGIONS: Set<MuscleRegion> = (FRONT + BACK).map { it.region }.toSet()
}
