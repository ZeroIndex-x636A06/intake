package com.zeroindex.intake.domain.script

import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.training.ExpressionEvaluator
import com.zeroindex.intake.domain.training.ExpressionResult
import com.zeroindex.intake.domain.training.RuleParams
import com.zeroindex.intake.domain.units.UnitConverter

// ---- AST --------------------------------------------------------------

/** The unit a program was written in. Everything stored is kilograms; see [Mass.kg]. */
enum class ScriptWeightUnit(val label: String) {
    KG("kg"),
    LB("lb"),
}

/** A weight as written. Equality is on the written value, so printing and parsing round-trip exactly. */
data class Mass(val value: Double, val unit: ScriptWeightUnit) {
    val kg: Double
        get() = if (unit == ScriptWeightUnit.KG) value else UnitConverter.lbToKg(value)

    fun text(): String = "${ScriptText.fmt(value)} ${unit.label}"
}

sealed interface RuleSpec {
    /** The existing rule type this maps to; null for [NoProgression]. */
    val type: ProgressionType?

    data class Linear(
        val increment: Mass,
        val deloadPercent: Double? = null,
        /** Consecutive missed sessions before the deload; null means the rule default (2). */
        val deloadAfter: Int? = null,
    ) : RuleSpec {
        override val type get() = ProgressionType.LINEAR
    }

    data class DoubleProgression(val increment: Mass) : RuleSpec {
        override val type get() = ProgressionType.DOUBLE_PROGRESSION
    }

    data object RpeBased : RuleSpec {
        override val type get() = ProgressionType.RPE_BASED
    }

    data class PercentOf1rm(val percent: Double) : RuleSpec {
        override val type get() = ProgressionType.PERCENTAGE_1RM
    }

    data class Custom(val expression: String) : RuleSpec {
        override val type get() = ProgressionType.CUSTOM
    }

    data object NoProgression : RuleSpec {
        override val type: ProgressionType? get() = null
    }

    /** The params the existing evaluator reads (kg, as stored). */
    fun toRuleParams(): RuleParams = when (this) {
        is Linear -> RuleParams(
            incrementKg = increment.kg,
            deloadPercent = deloadPercent ?: RuleParams().deloadPercent,
            failuresBeforeDeload = deloadAfter ?: RuleParams().failuresBeforeDeload,
        )
        is DoubleProgression -> RuleParams(incrementKg = increment.kg)
        is PercentOf1rm -> RuleParams(percentageOf1rm = percent)
        is Custom -> RuleParams(expression = expression)
        RpeBased, NoProgression -> RuleParams()
    }

    /** The `params` JSON for a ProgressionRuleEntity, keys named like [RuleParams]. */
    fun paramsJson(): String {
        val p = toRuleParams()
        return when (this) {
            is Linear -> """{"incrementKg":${ScriptText.fmt(p.incrementKg)},"deloadPercent":${ScriptText.fmt(p.deloadPercent)},"failuresBeforeDeload":${p.failuresBeforeDeload}}"""
            is DoubleProgression -> """{"incrementKg":${ScriptText.fmt(p.incrementKg)}}"""
            is PercentOf1rm -> """{"percentageOf1rm":${ScriptText.fmt(p.percentageOf1rm)}}"""
            is Custom -> """{"expression":"${expression.replace("\\", "\\\\").replace("\"", "\\\"")}"}"""
            RpeBased, NoProgression -> "{}"
        }
    }
}

sealed interface Reps {
    data class Fixed(val reps: Int) : Reps
    data class Range(val min: Int, val max: Int) : Reps
    data object Amrap : Reps

    /** ProgramExerciseEntity.targetRepsMin. AMRAP has no floor, so 1. */
    val entityMin: Int get() = when (this) { is Fixed -> reps; is Range -> min; Amrap -> 1 }

    /** ProgramExerciseEntity.targetRepsMax. AMRAP has no ceiling; [AMRAP_ENTITY_MAX] stands in. */
    val entityMax: Int get() = when (this) { is Fixed -> reps; is Range -> max; Amrap -> AMRAP_ENTITY_MAX }

    companion object {
        const val AMRAP_ENTITY_MAX = 99
    }
}

data class ExerciseDoc(
    val name: String,
    val sets: Int,
    val reps: Reps,
    val rpe: Double? = null,
    /** Starting weight, or the added load when [bodyweight] is true. */
    val weight: Mass? = null,
    val bodyweight: Boolean = false,
    /** Null: use the program's default rest. */
    val restSec: Int? = null,
    /** Null: use the program's default rule. */
    val progress: RuleSpec? = null,
    val notes: List<String> = emptyList(),
)

data class DayDoc(
    val name: String,
    val notes: List<String> = emptyList(),
    val exercises: List<ExerciseDoc> = emptyList(),
)

data class ProgramDoc(
    val name: String,
    val units: ScriptWeightUnit = ScriptWeightUnit.KG,
    /** Default rest for exercises that give none. Null: the exercise library default applies. */
    val restSec: Int? = null,
    val progress: RuleSpec? = null,
    val notes: List<String> = emptyList(),
    val days: List<DayDoc> = emptyList(),
) {
    /** The rest an exercise ends up with, or null when neither it nor the program says. */
    fun effectiveRest(e: ExerciseDoc): Int? = e.restSec ?: restSec

    fun effectiveProgress(e: ExerciseDoc): RuleSpec? = e.progress ?: progress
}

// ---- parser ---------------------------------------------------------------

object ProgramScript {
    internal val HEADER_KEYS = listOf("units", "rest", "progress", "notes")
    internal val EXERCISE_KEYS = listOf("sets", "reps", "rpe", "weight", "rest", "progress", "note")
    private val DAY_KEYS = listOf("note")

    private val HEADER_ALIAS = mapOf(
        "unit" to "units", "units" to "units", "rest" to "rest", "resttime" to "rest",
        "progress" to "progress", "progression" to "progress", "note" to "notes", "notes" to "notes",
    )
    private val EXERCISE_ALIAS = mapOf(
        "set" to "sets", "sets" to "sets", "rep" to "reps", "reps" to "reps", "rpe" to "rpe",
        "weight" to "weight", "load" to "weight", "rest" to "rest", "resttime" to "rest",
        "progress" to "progress", "progression" to "progress", "note" to "note", "notes" to "note",
    )
    private val DAY_ALIAS = mapOf("note" to "note", "notes" to "note")

    fun parse(text: String): ScriptResult<ProgramDoc> {
        val sink = DiagnosticSink()
        val doc = try {
            val lines = ScriptText.prepare(text, sink)
            if (lines == null) null else ProgramParser(lines, sink).run()
        } catch (e: Exception) {
            sink.report(DiagnosticCode.NOT_AN_INTAKE_SCRIPT, 1, 1, "This file could not be read as a program.")
            null
        }
        val diagnostics = sink.sorted()
        return ScriptResult(if (diagnostics.any { it.severity == Severity.ERROR }) null else doc, diagnostics)
    }

    internal fun parseMass(text: String, default: ScriptWeightUnit): Mass? {
        val m = Regex("""(\d+(?:\.\d+)?)\s*(kgs?|kilograms?|lbs?|pounds?)?""", RegexOption.IGNORE_CASE)
            .matchEntire(text.trim()) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        val unit = when (m.groupValues[2].lowercase().firstOrNull()) {
            null -> default
            'k' -> ScriptWeightUnit.KG
            else -> ScriptWeightUnit.LB
        }
        return Mass(value, unit)
    }

    private class ExerciseBuilder(val name: String, val line: Int, val col: Int) {
        var sets: Int? = null
        var reps: Reps? = null
        var rpe: Double? = null
        var weight: Mass? = null
        var bodyweight = false
        var weightSeen = false
        var restSec: Int? = null
        var progress: RuleSpec? = null
        var progressLine = 0
        var progressCol = 0
        val notes = ArrayList<String>()
        val seen = HashSet<String>()
    }

    private class DayBuilder(val name: String, val line: Int) {
        val notes = ArrayList<String>()
        val exercises = ArrayList<ExerciseDoc>()
    }

    private class RawField(val key: String, val value: String, val line: Int, val col: Int)

    private class ProgramParser(val lines: List<SrcLine>, val sink: DiagnosticSink) {
        var units = ScriptWeightUnit.KG
        var restSec: Int? = null
        var progress: RuleSpec? = null
        val notes = ArrayList<String>()
        val days = ArrayList<DayDoc>()
        var name = ""
        var day: DayBuilder? = null
        var ex: ExerciseBuilder? = null
        val rawHeader = ArrayList<RawField>()
        var headerResolved = false
        var lastLine = 1

        fun run(): ProgramDoc? {
            var started = false
            for (line in lines) {
                lastLine = line.no
                if (line.isBlank) continue
                if (!started) {
                    started = true
                    if (!title(line)) return null
                    continue
                }
                val h = ScriptText.heading(line)
                if (h != null) {
                    heading(line, h)
                    continue
                }
                when {
                    day == null -> headerLine(line)
                    ex == null -> dayLine(line)
                    else -> exerciseLine(line)
                }
            }
            if (!started) {
                sink.report(DiagnosticCode.NOT_AN_INTAKE_SCRIPT, 1, 1, "The file is empty. A program starts with \"# Program: <name>\".")
                return null
            }
            resolveHeader()
            finishExercise()
            finishDay()
            if (days.isEmpty()) {
                sink.report(DiagnosticCode.NO_DAYS, lastLine, 1, "The program has no days. Add a \"## Day name\" heading, then \"### Exercise\" blocks under it.")
            }
            return ProgramDoc(name, units, restSec, progress, notes.toList(), days.toList())
        }

        fun title(line: SrcLine): Boolean {
            val h = ScriptText.heading(line)
            val m = h?.let { Regex("""(?i)^program\s*:\s*(.*)$""").matchEntire(it.text) }
            if (h == null || h.level != 1 || m == null) {
                sink.report(
                    DiagnosticCode.NOT_AN_INTAKE_SCRIPT, line.no, line.indent + 1,
                    "A program file must start with \"# Program: <name>\", found \"${line.trimmed.take(40)}\".",
                )
                return false
            }
            name = m.groupValues[1].trim()
            if (name.isEmpty()) {
                sink.report(DiagnosticCode.MISSING_PROGRAM_NAME, line.no, line.text.length + 1, "The program needs a name: \"# Program: Strength Block A\".")
            }
            return true
        }

        fun heading(line: SrcLine, h: Heading) {
            when (h.level) {
                1 -> sink.report(
                    DiagnosticCode.UNEXPECTED_HEADING, line.no, h.textCol.coerceAtLeast(1),
                    "A second \"# \" heading. Use \"## \" for a day and \"### \" for an exercise.",
                )
                2 -> {
                    resolveHeader()
                    finishExercise()
                    finishDay()
                    if (h.text.isEmpty()) {
                        sink.report(DiagnosticCode.UNEXPECTED_HEADING, line.no, h.textCol, "This day has no name: \"## Upper A\".")
                    }
                    day = DayBuilder(h.text.ifEmpty { "Day ${days.size + 1}" }, line.no)
                }
                3 -> {
                    resolveHeader()
                    finishExercise()
                    if (day == null) {
                        sink.report(
                            DiagnosticCode.EXERCISE_OUTSIDE_DAY, line.no, 1,
                            "Exercise \"${h.text}\" comes before any day. Add a \"## Day name\" heading first.",
                        )
                        day = DayBuilder("Day 1", line.no)
                    }
                    if (h.text.isEmpty()) {
                        sink.report(DiagnosticCode.EMPTY_EXERCISE_NAME, line.no, h.textCol, "This exercise has no name: \"### Bench Press\".")
                    }
                    ex = ExerciseBuilder(h.text, line.no, line.indent + 1)
                }
                else -> sink.report(
                    DiagnosticCode.UNEXPECTED_HEADING, line.no, line.indent + 1,
                    "\"${"#".repeat(h.level)}\" headings are not used in programs. Use # program, ## day, ### exercise.",
                )
            }
        }

        /** Reports unknown key / missing colon / stray text. */
        fun unknownLine(line: SrcLine, label: Label?, validKeys: List<String>, hint: String) {
            if (label != null) {
                val sug = ScriptText.suggest(label.raw, validKeys)
                sink.report(
                    DiagnosticCode.UNKNOWN_KEY, line.no, label.keyCol,
                    "Unknown field \"${label.raw}\"." +
                        (if (sug != null) " Did you mean \"$sug\"?" else "") +
                        " Fields here: ${validKeys.joinToString(", ")}.",
                )
                return
            }
            val first = line.trimmed.trimStart('-', '*', '+', ' ').split(Regex("""\s+""")).firstOrNull().orEmpty()
            val known = validKeys.firstOrNull { ScriptText.key(it) == ScriptText.key(first) }
            if (known != null) {
                sink.report(
                    DiagnosticCode.MISSING_COLON, line.no, line.indent + 1,
                    "\"$first\" needs a colon after it, like \"$known: ${line.trimmed.substringAfter(' ', "...")}\".",
                )
            } else {
                sink.report(
                    DiagnosticCode.UNEXPECTED_TEXT, line.no, line.indent + 1,
                    "$hint, found \"${line.trimmed.take(50)}\".",
                )
            }
        }

        fun headerLine(line: SrcLine) {
            val label = ScriptText.label(line, allowBullet = true)
            val key = label?.let { HEADER_ALIAS[it.key] }
            if (label == null || key == null) {
                unknownLine(line, label, HEADER_KEYS, "Expected a setting such as \"units: lb\" or a \"## Day\" heading")
                return
            }
            if (label.value.isEmpty()) {
                sink.report(DiagnosticCode.EMPTY_VALUE, line.no, label.valueCol, "\"${label.raw}:\" needs a value after the colon.")
                return
            }
            if (key != "notes" && rawHeader.any { it.key == key }) {
                sink.report(DiagnosticCode.DUPLICATE_KEY, line.no, label.keyCol, "\"$key\" is already set above; give it once.")
                return
            }
            rawHeader += RawField(key, label.value, line.no, label.valueCol)
        }

        fun resolveHeader() {
            if (headerResolved) return
            headerResolved = true
            rawHeader.firstOrNull { it.key == "units" }?.let { f ->
                when (f.value.trim().lowercase()) {
                    "kg", "kgs", "kilograms", "kilogram" -> units = ScriptWeightUnit.KG
                    "lb", "lbs", "pounds", "pound" -> units = ScriptWeightUnit.LB
                    else -> sink.report(DiagnosticCode.UNKNOWN_UNITS, f.line, f.col, "Units must be lb or kg, found \"${f.value}\".")
                }
            }
            rawHeader.filter { it.key == "notes" }.forEach { notes += it.value }
            rawHeader.firstOrNull { it.key == "rest" }?.let { f -> restSec = rest(f.value, f.line, f.col) }
            rawHeader.firstOrNull { it.key == "progress" }?.let { f -> progress = rule(f.value, f.line, f.col) }
        }

        fun dayLine(line: SrcLine) {
            val label = ScriptText.label(line, allowBullet = true)
            val key = label?.let { DAY_ALIAS[it.key] }
            if (label != null && key == null && EXERCISE_ALIAS.containsKey(label.key)) {
                sink.report(
                    DiagnosticCode.FIELD_OUTSIDE_EXERCISE, line.no, label.keyCol,
                    "\"${label.raw}:\" belongs to an exercise. Put a \"### Exercise name\" heading above it.",
                )
                return
            }
            if (label == null || key == null) {
                unknownLine(line, label, DAY_KEYS, "Expected \"note: ...\" or a \"### Exercise\" heading")
                return
            }
            if (label.value.isEmpty()) {
                sink.report(DiagnosticCode.EMPTY_VALUE, line.no, label.valueCol, "\"${label.raw}:\" needs a value after the colon.")
                return
            }
            day!!.notes += label.value
        }

        fun exerciseLine(line: SrcLine) {
            val e = ex!!
            val short = Regex("""^[-*+]?\s*(\d+)\s*[xX×]\s*(.+)$""").matchEntire(line.trimmed)
            if (short != null && ScriptText.label(line, true) == null) {
                val col = line.indent + 1
                if ("sets" in e.seen || "reps" in e.seen) {
                    sink.report(DiagnosticCode.DUPLICATE_KEY, line.no, col, "sets and reps are already given for this exercise; use one or the other.")
                    return
                }
                e.seen += "sets"; e.seen += "reps"
                e.sets = sets(short.groupValues[1], line.no, col)
                e.reps = reps(short.groupValues[2], line.no, col + line.trimmed.indexOf(short.groupValues[2]))
                return
            }
            val label = ScriptText.label(line, allowBullet = true)
            val key = label?.let { EXERCISE_ALIAS[it.key] }
            if (label == null || key == null) {
                unknownLine(line, label, EXERCISE_KEYS, "Expected a field such as \"sets: 4\" or \"reps: 6-8\", or a new heading")
                return
            }
            if (label.value.isEmpty()) {
                sink.report(DiagnosticCode.EMPTY_VALUE, line.no, label.valueCol, "\"${label.raw}:\" needs a value after the colon.")
                return
            }
            if (key != "note" && !e.seen.add(key)) {
                sink.report(DiagnosticCode.DUPLICATE_KEY, line.no, label.keyCol, "\"$key\" is already set for \"${e.name}\"; give it once.")
                return
            }
            val v = label.value
            val c = label.valueCol
            when (key) {
                "sets" -> e.sets = sets(v, line.no, c)
                "reps" -> e.reps = reps(v, line.no, c)
                "rpe" -> e.rpe = rpe(v, line.no, c)
                "weight" -> weight(v, line.no, c, e)
                "rest" -> e.restSec = rest(v, line.no, c)
                "progress" -> {
                    e.progress = rule(v, line.no, c)
                    e.progressLine = line.no
                    e.progressCol = c
                }
                "note" -> e.notes += v
            }
        }

        fun sets(v: String, line: Int, col: Int): Int? {
            val s = v.trim().lowercase().removeSuffix("sets").removeSuffix("set").trim()
            val n = s.toIntOrNull()
            if (n == null || n !in 1..30 || !Regex("""\d+""").matches(s)) {
                sink.report(DiagnosticCode.BAD_SETS, line, col, "Expected a number of sets from 1 to 30 like \"4\", found \"$v\".")
                return null
            }
            return n
        }

        fun reps(v: String, line: Int, col: Int): Reps? {
            val s = v.trim().lowercase().removeSuffix("reps").removeSuffix("rep").trim()
            if (s == "max" || s == "amrap") return Reps.Amrap
            Regex("""(\d{1,3})""").matchEntire(s)?.let {
                val n = it.groupValues[1].toInt()
                if (n in 1..100) return Reps.Fixed(n)
            }
            Regex("""(\d{1,3})\s*(?:-|–|—|to)\s*(\d{1,3})""").matchEntire(s)?.let {
                val a = it.groupValues[1].toInt()
                val b = it.groupValues[2].toInt()
                if (a !in 1..100 || b !in 1..100) {
                    sink.report(DiagnosticCode.BAD_REPS, line, col, "Reps must be between 1 and 100, found \"$v\".")
                    return null
                }
                if (a > b) {
                    sink.report(DiagnosticCode.BAD_REPS, line, col, "The rep range \"$v\" runs backwards; write the smaller number first, like \"${b}-${a}\".")
                    return null
                }
                return if (a == b) Reps.Fixed(a) else Reps.Range(a, b)
            }
            sink.report(DiagnosticCode.BAD_REPS, line, col, "Expected reps like \"8\", \"6-8\", \"6 to 8\" or \"max\", found \"$v\".")
            return null
        }

        fun rpe(v: String, line: Int, col: Int): Double? {
            val s = v.trim().lowercase().removePrefix("@").removePrefix("rpe").trim()
            val d = ScriptText.strictNumber(s)
            if (d == null || d < 1.0 || d > 10.0) {
                sink.report(DiagnosticCode.BAD_RPE, line, col, "RPE must be a number from 1 to 10 like \"8\" or \"7.5\", found \"$v\".")
                return null
            }
            return d
        }

        fun weight(v: String, line: Int, col: Int, e: ExerciseBuilder) {
            val s = v.trim()
            val bw = Regex("""(?i)^(?:bw|body\s*weight)(?:\s*\+\s*(.+))?$""").matchEntire(s)
            if (bw != null) {
                e.bodyweight = true
                val extra = bw.groupValues[1]
                if (extra.isNotEmpty()) {
                    val m = ProgramScript.parseMass(extra, units)
                    if (m == null) {
                        sink.report(DiagnosticCode.BAD_WEIGHT, line, col, "Expected added weight like \"bodyweight + 25 lb\", found \"$v\".")
                    } else {
                        e.weight = m
                    }
                }
                return
            }
            val m = ProgramScript.parseMass(s, units)
            if (m == null) {
                sink.report(
                    DiagnosticCode.BAD_WEIGHT, line, col,
                    "Expected a weight like \"185\", \"185 lb\", \"80 kg\" or \"bodyweight\", found \"$v\".",
                )
            } else {
                e.weight = m
            }
        }

        fun rest(v: String, line: Int, col: Int): Int? {
            val sec = ScriptText.restSeconds(v)
            if (sec == null || sec < 5 || sec > 3600) {
                val extra = if (Regex("""\d+""").matches(v.trim())) " A bare number is ambiguous: write \"${v.trim()}s\" or \"${v.trim()} min\"." else ""
                sink.report(
                    DiagnosticCode.BAD_REST, line, col,
                    "Expected a rest between 5 seconds and 1 hour like \"2:30\", \"150s\" or \"2 min\", found \"$v\".$extra",
                )
                return null
            }
            return sec
        }

        fun rule(v: String, line: Int, col: Int): RuleSpec? = ProgramScript.parseRule(v, line, col, units, sink)

        fun finishExercise() {
            val e = ex ?: return
            ex = null
            val d = day ?: return
            if (e.sets == null && "sets" !in e.seen) {
                sink.report(DiagnosticCode.MISSING_SETS, e.line, e.col, "\"${e.name}\" has no sets. Add a line like \"sets: 4\".")
            }
            if (e.reps == null && "reps" !in e.seen) {
                sink.report(DiagnosticCode.MISSING_REPS, e.line, e.col, "\"${e.name}\" has no reps. Add a line like \"reps: 6-8\" (or \"reps: max\").")
            }
            val sets = e.sets
            val reps = e.reps
            if (sets == null || reps == null) return
            if (e.progress == RuleSpec.RpeBased && e.rpe == null) {
                sink.report(DiagnosticCode.RPE_RULE_NEEDS_RPE, e.progressLine, e.progressCol, "\"${e.name}\" uses the rpe rule but has no \"rpe:\" target. Add a line like \"rpe: 8\".")
            }
            val effective = e.progress ?: progress
            if (e.progress == null && effective == RuleSpec.RpeBased && e.rpe == null) {
                sink.report(DiagnosticCode.RPE_RULE_WITHOUT_RPE, e.line, e.col, "\"${e.name}\" inherits the program's rpe rule but has no \"rpe:\" target, so its weight will not adjust.")
            }
            if (effective is RuleSpec.DoubleProgression && reps == Reps.Amrap) {
                sink.report(DiagnosticCode.AMRAP_WITH_DOUBLE, e.line, e.col, "\"${e.name}\" uses double progression with max reps, which has no top of range to reach.")
            }
            d.exercises += ExerciseDoc(
                name = e.name, sets = sets, reps = reps, rpe = e.rpe, weight = e.weight,
                bodyweight = e.bodyweight, restSec = e.restSec, progress = e.progress, notes = e.notes.toList(),
            )
        }

        fun finishDay() {
            val d = day ?: return
            day = null
            if (d.exercises.isEmpty()) {
                sink.report(DiagnosticCode.EMPTY_DAY, d.line, 1, "Day \"${d.name}\" has no exercises.")
            }
            if (days.any { ScriptText.key(it.name) == ScriptText.key(d.name) }) {
                sink.report(DiagnosticCode.DUPLICATE_DAY, d.line, 1, "There is already a day called \"${d.name}\".")
            }
            days += DayDoc(d.name, d.notes.toList(), d.exercises.toList())
        }
    }

    // ---- progress rules ------------------------------------------------

    private val RULE_HELP =
        "linear, add 5 lb | double progression, add 5 lb | rpe | 80% of max | none | custom: <expression>"

    internal fun parseRule(v: String, line: Int, col: Int, units: ScriptWeightUnit, sink: DiagnosticSink): RuleSpec? {
        val text = v.trim()
        val lower = text.lowercase()

        // custom: <expression>, or "= <expression>"
        val custom = Regex("""(?i)^custom\b\s*[:=]?\s*(.*)$""").matchEntire(text)
            ?: Regex("""^=\s*(.*)$""").matchEntire(text)
        if (custom != null) {
            val expr = custom.groupValues[1].trim()
            val exprCol = col + (text.length - custom.groupValues[1].trimStart().length).coerceAtLeast(0)
            if (expr.isEmpty()) {
                sink.report(DiagnosticCode.BAD_EXPRESSION, line, exprCol, "A custom rule needs an expression after \"custom:\", like \"custom: weight + 2.5\".")
                return null
            }
            val result = ExpressionEvaluator.validate(expr)
            if (result is ExpressionResult.Error) {
                sink.report(DiagnosticCode.BAD_EXPRESSION, line, exprCol, "The custom rule \"$expr\" is not valid: ${result.message}")
                return null
            }
            return RuleSpec.Custom(expr)
        }

        if (Regex("""(none|off|no progression|no rule)""").matches(lower)) return RuleSpec.NoProgression
        if (Regex("""rpe([\s-]*based)?""").matches(lower)) return RuleSpec.RpeBased

        val pct = Regex("""(\d+(?:\.\d+)?)\s*%\s*(?:of\s*(?:my\s*|your\s*)?(?:1\s*rm|one\s*rep\s*max|1\s*rep\s*max|max))?""").matchEntire(lower)
            ?: Regex("""1\s*rm\s*(\d+(?:\.\d+)?)\s*%""").matchEntire(lower)
        if (pct != null) {
            val p = pct.groupValues[1].toDouble()
            if (p <= 0.0 || p > 100.0) {
                sink.report(DiagnosticCode.BAD_PERCENT, line, col, "A percentage of max must be above 0 and at most 100, found \"$text\".")
                return null
            }
            return RuleSpec.PercentOf1rm(p)
        }
        if (Regex("""\d+(\.\d+)?\s*%.*""").matches(lower)) {
            sink.report(DiagnosticCode.BAD_PERCENT, line, col, "Expected a percentage of max like \"80% of max\", found \"$text\".")
            return null
        }

        val head = Regex("""(linear|double)(?:[\s-]*progression)?\b(.*)""").matchEntire(lower)
        if (head == null) {
            val first = lower.split(Regex("""[\s,:]+""")).firstOrNull().orEmpty()
            val sug = ScriptText.suggest(first, listOf("linear", "double", "rpe", "none", "custom"))
            sink.report(
                DiagnosticCode.BAD_RULE, line, col,
                "Unknown progress rule \"$text\"." + (if (sug != null) " Did you mean \"$sug\"?" else "") + " Write one of: $RULE_HELP.",
            )
            return null
        }
        val isLinear = head.groupValues[1] == "linear"
        var rest = head.groupValues[2].replace(',', ' ')

        var deloadPct: Double? = null
        var deloadAfter: Int? = null
        val deload = Regex("""\bdeload\b\s*(?:by|of)?\s*(\d+(?:\.\d+)?)\s*%?(?:\s*after\s*(\d+)(?:\s*(?:misses|missed|failures|failure|fails|fail|sessions|times|workouts))?)?""")
            .find(rest)
        if (deload != null) {
            if (!isLinear) {
                sink.report(DiagnosticCode.BAD_DELOAD, line, col, "Only a linear rule can deload. Use \"linear, add 5 lb, deload 10% after 3 misses\".")
                return null
            }
            deloadPct = deload.groupValues[1].toDouble()
            deloadAfter = deload.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()
            if (deloadPct <= 0.0 || deloadPct >= 100.0 || (deloadAfter != null && deloadAfter < 1)) {
                sink.report(DiagnosticCode.BAD_DELOAD, line, col, "A deload must be between 0 and 100 percent after at least 1 miss, like \"deload 10% after 3 misses\".")
                return null
            }
            rest = rest.removeRange(deload.range)
        } else if (rest.contains("deload")) {
            sink.report(DiagnosticCode.BAD_DELOAD, line, col, "Expected a deload like \"deload 10% after 3 misses\", found \"${text}\".")
            return null
        }

        rest = rest.replace(Regex("""\b(?:each|every|per)\s+(?:session|time|workout|week)\b"""), " ")
        val inc = Regex("""(?:\b(?:add|plus|increase(?:\s+by)?|up|by)\b\s*|\+\s*)?(\d+(?:\.\d+)?)\s*(kgs?|kilograms?|lbs?|pounds?)?\b""").find(rest)
        if (inc == null) {
            val leftover = rest.trim()
            if (leftover.isEmpty()) {
                sink.report(
                    DiagnosticCode.RULE_NEEDS_INCREMENT, line, col,
                    "\"$text\" needs an increment, like \"${head.groupValues[1]}, add 5 ${units.label}\".",
                )
            } else {
                sink.report(DiagnosticCode.BAD_INCREMENT, line, col, "Expected an increment like \"add 5 ${units.label}\", found \"$leftover\".")
            }
            return null
        }
        val mass = ProgramScript.parseMass(inc.groupValues[1] + inc.groupValues[2], units)
        if (mass == null) {
            sink.report(DiagnosticCode.BAD_INCREMENT, line, col, "The increment must be above zero, found \"${inc.value.trim()}\".")
            return null
        }
        val leftover = rest.removeRange(inc.range).trim()
        if (leftover.isNotEmpty()) {
            sink.report(DiagnosticCode.BAD_RULE, line, col, "Did not understand \"$leftover\" in \"$text\". Write one of: $RULE_HELP.")
            return null
        }
        return if (isLinear) RuleSpec.Linear(mass, deloadPct, deloadAfter) else RuleSpec.DoubleProgression(mass)
    }

    internal fun printRule(r: RuleSpec): String = when (r) {
        is RuleSpec.Linear -> buildString {
            append("linear, add ${r.increment.text()}")
            if (r.deloadPercent != null) {
                append(", deload ${ScriptText.fmt(r.deloadPercent)}%")
                if (r.deloadAfter != null) append(" after ${r.deloadAfter} misses")
            }
        }
        is RuleSpec.DoubleProgression -> "double progression, add ${r.increment.text()}"
        RuleSpec.RpeBased -> "rpe"
        is RuleSpec.PercentOf1rm -> "${ScriptText.fmt(r.percent)}% of max"
        is RuleSpec.Custom -> "custom: ${r.expression}"
        RuleSpec.NoProgression -> "none"
    }

    // ---- printer -----------------------------------------------------

    /** Canonical text for [doc]. `parse(print(doc))` yields an equal document. */
    fun print(doc: ProgramDoc): String = buildString {
        append("# Program: ${doc.name}\n")
        append("units: ${doc.units.label}\n")
        doc.restSec?.let { append("rest: ${ScriptText.clock(it)}\n") }
        doc.progress?.let { append("progress: ${printRule(it)}\n") }
        doc.notes.forEach { append("notes: $it\n") }
        doc.days.forEach { d ->
            append("\n## ${d.name}\n")
            d.notes.forEach { append("note: $it\n") }
            d.exercises.forEach { e ->
                append("\n### ${e.name}\n")
                append("sets: ${e.sets}\n")
                append(
                    "reps: " + when (val r = e.reps) {
                        is Reps.Fixed -> r.reps.toString()
                        is Reps.Range -> "${r.min}-${r.max}"
                        Reps.Amrap -> "max"
                    } + "\n",
                )
                e.rpe?.let { append("rpe: ${ScriptText.fmt(it)}\n") }
                if (e.bodyweight) {
                    append("weight: bodyweight" + (e.weight?.let { " + ${it.text()}" } ?: "") + "\n")
                } else {
                    e.weight?.let { append("weight: ${it.text()}\n") }
                }
                e.restSec?.let { append("rest: ${ScriptText.clock(it)}\n") }
                e.progress?.let { append("progress: ${printRule(it)}\n") }
                e.notes.forEach { append("note: $it\n") }
            }
        }
    }
}
