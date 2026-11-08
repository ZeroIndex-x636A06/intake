package com.zeroindex.intake.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.zeroindex.intake.R
import com.zeroindex.intake.domain.report.ReportDay
import com.zeroindex.intake.domain.report.WeeklyReport
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.WeightUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Amendment §3's printable weekly check-in, drawn straight onto a PDF canvas
 * at US Letter (612 × 792 pt).
 *
 * Designed to survive a black-and-white printer: series are told apart by
 * fill and dash pattern, never by colour alone, every chart carries a legend
 * in words, and every chart has the table or figures it summarises beside it.
 */
@Singleton
class PdfReportRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val W = 612f
        const val H = 792f
        const val M = 40f
        const val CONTENT_W = W - 2 * M
    }

    private val heading: Typeface = Typeface.create(
        ResourcesCompat.getFont(context, R.font.manrope_variable) ?: Typeface.SANS_SERIF, 800, false,
    )
    private val body: Typeface = ResourcesCompat.getFont(context, R.font.dm_sans_variable) ?: Typeface.SANS_SERIF
    private val bold: Typeface = Typeface.create(body, 700, false)

    private val black = 0xFF1C1D20.toInt()
    private val mid = 0xFF7A7B80.toInt()
    private val light = 0xFFC9CACE.toInt()
    private val rule = 0xFFE0E0E4.toInt()

    private fun paint(size: Float, face: Typeface = body, color: Int = black) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
    }

    private fun stroke(color: Int, width: Float, dash: FloatArray? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
        dash?.let { pathEffect = DashPathEffect(it, 0f) }
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }

    /** Renders [report] to a file in the cache and returns it. */
    suspend fun render(report: WeeklyReport, unit: WeightUnit): File = withContext(Dispatchers.Default) {
        val doc = PdfDocument()
        var pageNo = 0
        fun newPage(): PdfDocument.Page {
            pageNo++
            return doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
        }
        val range = "${report.weekStart.format(DateTimeFormatter.ofPattern("d MMM"))} – " +
            report.weekEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy"))

        // ---- Page 1: at a glance, macros --------------------------------
        var page = newPage()
        var c = page.canvas
        var y = header(c, range, pageNo)
        y = glance(c, y, report, unit)
        y = macroChart(c, y + 16, report)
        macroTable(c, y + 10, report)
        doc.finishPage(page)

        // ---- Page 2: energy, weight, sleep, heart -------------------------
        page = newPage()
        c = page.canvas
        y = header(c, range, pageNo)
        y = energyChart(c, y, report)
        y = weightChart(c, y + 14, report, unit)
        y = sleepChart(c, y + 14, report)
        heartChart(c, y + 14, report)
        doc.finishPage(page)

        // ---- Page 3+: training ------------------------------------------
        page = newPage()
        c = page.canvas
        y = header(c, range, pageNo)
        y = section(c, y, "Training summary")
        c.drawText(
            "${report.sessions.size} sessions · ${report.totalSets} working sets · " +
                "${"%,d".format(unit.fromKg(report.totalVolumeKg).roundToInt())} ${unit.label} total volume",
            M, y, paint(10.5f, bold),
        )
        y += 18
        if (report.sessions.isEmpty()) c.drawText("No sessions logged this week.", M, y, paint(10f, color = mid))
        for (session in report.sessions) {
            val needed = 34f + session.exercises.size * 14f + (if (session.notes != null) 14f else 0f)
            if (y + needed > H - M) {
                doc.finishPage(page)
                page = newPage()
                c = page.canvas
                y = header(c, range, pageNo)
            }
            c.drawLine(M, y, W - M, y, stroke(rule, 0.8f))
            y += 14
            c.drawText(
                "${session.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))} · ${session.title}" +
                    (session.minutes?.let { " · $it min" } ?: ""),
                M, y, paint(11f, heading),
            )
            c.drawText(
                "${"%,d".format(unit.fromKg(session.volumeKg).roundToInt())} ${unit.label}",
                W - M - paint(10f, bold).measureText("${"%,d".format(unit.fromKg(session.volumeKg).roundToInt())} ${unit.label}"),
                y, paint(10f, bold),
            )
            y += 15
            for (exercise in session.exercises) {
                c.drawText(exercise.name, M + 8, y, paint(9.5f, bold))
                c.drawText(exercise.describe { "${fmt(unit.fromKg(it))} ${unit.label}" }, M + 180, y, paint(9.5f))
                y += 13
            }
            session.notes?.let {
                c.drawText("Notes: ${it.take(110)}", M + 8, y, paint(9f, color = mid))
                y += 13
            }
            y += 4
        }
        doc.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "intake-week-${report.weekStart}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        file
    }

    // ---------------------------------------------------------------------

    private fun header(c: Canvas, range: String, page: Int): Float {
        c.drawText("Weekly check-in", M, M + 14, paint(20f, heading))
        c.drawText(range, M, M + 32, paint(11f, color = mid))
        val brand = "Intake · page $page"
        c.drawText(brand, W - M - paint(9f, color = mid).measureText(brand), M + 14, paint(9f, color = mid))
        c.drawLine(M, M + 42, W - M, M + 42, stroke(black, 1.2f))
        return M + 62
    }

    private fun section(c: Canvas, y: Float, title: String): Float {
        c.drawText(title.uppercase(), M, y, paint(9f, bold, mid).apply { letterSpacing = 0.1f })
        return y + 16
    }

    private fun glance(c: Canvas, top: Float, r: WeeklyReport, unit: WeightUnit): Float {
        var y = section(c, top, "Week at a glance · ${r.daysLogged} of 7 days logged")
        val burnSource = if (r.days.any { it.burnedFromDevice }) "device" else "estimate"
        val tiles = listOf(
            r.average { it.weightKg }?.let { "${fmt(unit.fromKg(it))} ${unit.label}" } to
                ("Avg weight" + (r.weightChangeKg?.let { " (${signed(unit.fromKg(it))} trend)" } ?: "")),
            r.average { it.kcal }?.let { "${"%,d".format(it.roundToInt())} kcal" } to
                ("Avg intake" + (r.kcalTarget?.let { " · target ${"%,d".format(it)}" } ?: "")),
            r.average { it.burned }?.let { "${"%,d".format(it.roundToInt())} kcal" } to "Avg burn ($burnSource)",
            r.average { it.sleepMinutes }?.let { "%.1f h".format(it / 60) } to "Avg sleep",
            r.average { it.restingHr }?.let { "${it.roundToInt()} bpm" } to "Avg resting HR",
            r.average { it.steps }?.let { "%,d".format(it.roundToInt()) } to "Avg steps",
        )
        val tileW = (CONTENT_W - 2 * 10) / 3
        tiles.forEachIndexed { i, (value, label) ->
            val x = M + (i % 3) * (tileW + 10)
            val ty = y + (i / 3) * 56
            c.drawRoundRect(RectF(x, ty, x + tileW, ty + 48), 8f, 8f, stroke(light, 1f))
            c.drawText(value ?: "—", x + 10, ty + 22, paint(15f, heading))
            c.drawText(label, x + 10, ty + 38, paint(8.5f, color = mid))
        }
        return y + 2 * 56
    }

    /** Grouped bars per day: protein solid, carbs mid-grey, fat outlined. */
    private fun macroChart(c: Canvas, top: Float, r: WeeklyReport): Float {
        var y = section(c, top, "Daily macros (g)")
        val h = 120f
        val maxG = max(
            r.days.maxOf { listOfNotNull(it.protein, it.carbs, it.fat).maxOrNull() ?: 0.0 },
            (r.proteinTarget ?: 0).toDouble(),
        ).coerceAtLeast(50.0) * 1.1
        val slot = CONTENT_W / 7
        c.drawLine(M, y + h, W - M, y + h, stroke(black, 0.8f))
        r.days.forEachIndexed { i, d ->
            val x0 = M + i * slot + slot * 0.15f
            val bw = slot * 0.7f / 3
            listOf(d.protein to fill(black), d.carbs to fill(mid), d.fat to fill(0xFFFFFFFF.toInt())).forEachIndexed { k, (v, p) ->
                v ?: return@forEachIndexed
                val bh = (v / maxG * h).toFloat()
                val rect = RectF(x0 + k * bw, y + h - bh, x0 + (k + 1) * bw - 1, y + h)
                c.drawRect(rect, p)
                if (k == 2) c.drawRect(rect, stroke(black, 0.8f))
            }
            c.drawText(d.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase), x0, y + h + 12, paint(8.5f, color = mid))
        }
        r.proteinTarget?.let { t ->
            val ty = (y + h - t / maxG * h).toFloat()
            c.drawLine(M, ty, W - M, ty, stroke(black, 0.8f, floatArrayOf(4f, 3f)))
            c.drawText("protein target $t g", W - M - 90, ty - 3, paint(8f))
        }
        y += h + 24
        legend(
            c, y,
            listOf(
                "Protein" to { x: Float, yy: Float -> c.drawRect(x, yy - 7, x + 10, yy + 1, fill(black)) },
                "Carbs" to { x: Float, yy: Float -> c.drawRect(x, yy - 7, x + 10, yy + 1, fill(mid)) },
                "Fat" to { x: Float, yy: Float -> c.drawRect(x, yy - 7, x + 10, yy + 1, stroke(black, 0.8f)) },
                "Protein target" to { x: Float, yy: Float -> c.drawLine(x, yy - 3, x + 14, yy - 3, stroke(black, 0.8f, floatArrayOf(4f, 3f))) },
            ),
        )
        return y + 6
    }

    private fun macroTable(c: Canvas, top: Float, r: WeeklyReport) {
        val cols = listOf("Day", "kcal", "Protein g", "Carbs g", "Fat g", "Fiber g", "Sugar g")
        val colW = CONTENT_W / cols.size
        var y = top + 10
        cols.forEachIndexed { i, h -> c.drawText(h, M + i * colW, y, paint(8.5f, bold)) }
        y += 5
        c.drawLine(M, y, W - M, y, stroke(black, 0.8f))
        r.days.forEach { d ->
            y += 14
            val cells = listOf(
                d.date.format(DateTimeFormatter.ofPattern("EEE d")),
                d.kcal?.let { "%,d".format(it.roundToInt()) },
                d.protein?.roundToInt()?.toString(),
                d.carbs?.roundToInt()?.toString(),
                d.fat?.roundToInt()?.toString(),
                d.fiber?.roundToInt()?.toString(),
                d.sugar?.roundToInt()?.toString(),
            )
            cells.forEachIndexed { i, v -> c.drawText(v ?: "—", M + i * colW, y, paint(9f, if (i == 0) bold else body)) }
            c.drawLine(M, y + 4, W - M, y + 4, stroke(rule, 0.5f))
        }
        y += 14
        val avgs = listOf("Average", r.average { it.kcal }, r.average { it.protein }, r.average { it.carbs }, r.average { it.fat }, r.average { it.fiber }, r.average { it.sugar })
        avgs.forEachIndexed { i, v ->
            val text = when (v) { is String -> v; is Double -> if (i == 1) "%,d".format(v.roundToInt()) else v.roundToInt().toString(); else -> "—" }
            c.drawText(text, M + i * colW, y, paint(9f, bold))
        }
    }

    /** Calories in as grey bars, calories out as a black line with dots. */
    private fun energyChart(c: Canvas, top: Float, r: WeeklyReport): Float {
        var y = section(c, top, "Calories in vs burned")
        val h = 110f
        val maxK = max(
            r.days.maxOf { max(it.kcal ?: 0.0, it.burned ?: 0.0) },
            (r.kcalTarget ?: 0).toDouble(),
        ).coerceAtLeast(1_000.0) * 1.1
        val slot = CONTENT_W / 7
        c.drawLine(M, y + h, W - M, y + h, stroke(black, 0.8f))
        val path = Path()
        var started = false
        r.days.forEachIndexed { i, d ->
            val cx = M + i * slot + slot / 2
            d.kcal?.let { v ->
                val bh = (v / maxK * h).toFloat()
                c.drawRect(cx - slot * 0.25f, y + h - bh, cx + slot * 0.25f, y + h, fill(light))
            }
            d.burned?.let { v ->
                val py = (y + h - v / maxK * h).toFloat()
                if (!started) { path.moveTo(cx, py); started = true } else path.lineTo(cx, py)
                c.drawCircle(cx, py, 2.5f, fill(black))
            }
            c.drawText(d.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase), cx - 8, y + h + 12, paint(8.5f, color = mid))
        }
        c.drawPath(path, stroke(black, 1.4f))
        r.kcalTarget?.let { t ->
            val ty = (y + h - t / maxK * h).toFloat()
            c.drawLine(M, ty, W - M, ty, stroke(mid, 0.8f, floatArrayOf(4f, 3f)))
        }
        y += h + 24
        val burnLabel = if (r.days.any { it.burnedFromDevice }) "Burned (device estimate)" else "Burned (measured maintenance + workouts)"
        legend(
            c, y,
            listOf(
                "Eaten" to { x: Float, yy: Float -> c.drawRect(x, yy - 7, x + 10, yy + 1, fill(light)) },
                burnLabel to { x: Float, yy: Float -> c.drawLine(x, yy - 3, x + 14, yy - 3, stroke(black, 1.4f)) },
                "Target" to { x: Float, yy: Float -> c.drawLine(x, yy - 3, x + 14, yy - 3, stroke(mid, 0.8f, floatArrayOf(4f, 3f))) },
            ),
        )
        return y + 4
    }

    private fun weightChart(c: Canvas, top: Float, r: WeeklyReport, unit: WeightUnit): Float {
        var y = section(c, top, "Body weight (${unit.label})")
        val h = 90f
        val raw = r.days.mapIndexedNotNull { i, d -> d.weightKg?.let { i to unit.fromKg(it) } }
        val trend = r.weightTrend.map { (date, kg) -> (date.toEpochDay() - r.weekStart.toEpochDay()).toInt() to unit.fromKg(kg) }
        val all = raw.map { it.second } + trend.map { it.second }
        if (all.isEmpty()) {
            c.drawText("No weigh-ins this week.", M, y + 12, paint(10f, color = mid))
            return y + 24
        }
        val lo = all.min() - 1.0
        val hi = all.max() + 1.0
        val slot = CONTENT_W / 7
        fun px(i: Int) = M + i * slot + slot / 2
        fun py(v: Double) = (y + h - (v - lo) / (hi - lo) * h).toFloat()
        c.drawLine(M, y + h, W - M, y + h, stroke(black, 0.8f))
        if (trend.size >= 2) {
            val path = Path()
            trend.forEachIndexed { k, (i, v) -> if (k == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v)) }
            c.drawPath(path, stroke(black, 1.6f))
        }
        raw.forEach { (i, v) ->
            c.drawCircle(px(i), py(v), 3f, fill(0xFFFFFFFF.toInt()))
            c.drawCircle(px(i), py(v), 3f, stroke(black, 1f))
        }
        c.drawText(fmt(hi - 1.0), M - 2, y + 8, paint(8f, color = mid))
        c.drawText(fmt(lo + 1.0), M - 2, y + h - 2, paint(8f, color = mid))
        y += h + 16
        legend(
            c, y,
            listOf(
                "Weigh-in" to { x: Float, yy: Float -> c.drawCircle(x + 5, yy - 3, 3f, stroke(black, 1f)) },
                "Smoothed trend" to { x: Float, yy: Float -> c.drawLine(x, yy - 3, x + 14, yy - 3, stroke(black, 1.6f)) },
            ),
        )
        return y + 4
    }

    private fun sleepChart(c: Canvas, top: Float, r: WeeklyReport): Float {
        var y = section(c, top, "Sleep")
        val h = 70f
        val maxM = (r.days.maxOf { it.sleepMinutes ?: 0L }.coerceAtLeast(480)).toDouble() * 1.1
        val slot = CONTENT_W / 7
        c.drawLine(M, y + h, W - M, y + h, stroke(black, 0.8f))
        r.days.forEachIndexed { i, d ->
            val cx = M + i * slot + slot / 2
            d.sleepMinutes?.let { m ->
                val bh = (m / maxM * h).toFloat()
                c.drawRect(cx - slot * 0.25f, y + h - bh, cx + slot * 0.25f, y + h, fill(mid))
                c.drawText("%.1f".format(m / 60.0), cx - 8, y + h - bh - 3, paint(8f))
            }
        }
        y += h + 14
        val spread = r.bedtimeSpreadMinutes
        c.drawText(
            when {
                r.days.none { it.sleepMinutes != null } -> "No sleep data this week."
                spread != null -> "Hours per night. Bedtime varied by about ±${spread.roundToInt()} minutes."
                else -> "Hours per night."
            },
            M, y, paint(9f, color = mid),
        )
        return y + 4
    }

    private fun heartChart(c: Canvas, top: Float, r: WeeklyReport) {
        var y = section(c, top, "Heart rate (bpm)")
        val h = 70f
        val values = r.days.flatMap { listOfNotNull(it.restingHr, it.avgHr) }
        if (values.isEmpty()) {
            c.drawText("No heart rate data this week.", M, y + 12, paint(10f, color = mid))
            return
        }
        val lo = values.min() - 5
        val hi = values.max() + 5
        val slot = CONTENT_W / 7
        fun py(v: Double) = (y + h - (v - lo) / (hi - lo) * h).toFloat()
        c.drawLine(M, y + h, W - M, y + h, stroke(black, 0.8f))
        listOf(
            { d: ReportDay -> d.restingHr } to stroke(black, 1.4f),
            { d: ReportDay -> d.avgHr } to stroke(black, 1.2f, floatArrayOf(5f, 3f)),
        ).forEach { (sel, p) ->
            val path = Path()
            var started = false
            r.days.forEachIndexed { i, d ->
                sel(d)?.let { v ->
                    val x = M + i * slot + slot / 2
                    if (!started) { path.moveTo(x, py(v)); started = true } else path.lineTo(x, py(v))
                }
            }
            c.drawPath(path, p)
        }
        y += h + 16
        legend(
            c, y,
            listOf(
                "Resting" to { x: Float, yy: Float -> c.drawLine(x, yy - 3, x + 14, yy - 3, stroke(black, 1.4f)) },
                "All-day average" to { x: Float, yy: Float -> c.drawLine(x, yy - 3, x + 14, yy - 3, stroke(black, 1.2f, floatArrayOf(5f, 3f))) },
            ),
        )
    }

    private fun legend(c: Canvas, y: Float, entries: List<Pair<String, (Float, Float) -> Unit>>) {
        var x = M
        val p = paint(8.5f)
        entries.forEach { (label, mark) ->
            mark(x, y)
            c.drawText(label, x + 18, y, p)
            x += 18 + p.measureText(label) + 16
        }
    }

    private fun fmt(v: Double) = UnitConverter.round(v, 1).toString()
    private fun signed(v: Double) = (if (v > 0) "+" else "") + fmt(v)
}
