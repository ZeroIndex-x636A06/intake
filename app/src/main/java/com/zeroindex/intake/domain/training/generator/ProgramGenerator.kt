package com.zeroindex.intake.domain.training.generator

import com.zeroindex.intake.domain.model.ProgressionType
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The kinds of training day a split is built from. */
enum class DayType(val label: String, val muscles: List<String>) {
    FULL(
        "Full body",
        listOf("chest", "back", "shoulders", "quads", "hamstrings", "glutes", "biceps", "triceps", "calves", "core"),
    ),
    UPPER("Upper", listOf("chest", "back", "shoulders", "biceps", "triceps", "core")),
    LOWER("Lower", listOf("quads", "hamstrings", "glutes", "calves", "core")),
    PUSH("Push", listOf("chest", "shoulders", "triceps")),
    PULL("Pull", listOf("back", "biceps", "core")),
    LEGS("Legs", listOf("quads", "hamstrings", "glutes", "calves", "core")),
}

/** The evidence-based weekly hard-set ranges, kept in one place so the UI can show them. */
object VolumeGuide {
    fun range(experience: Experience): IntRange = when (experience) {
        Experience.NEW -> 8..10
        Experience.SOME -> 10..14
        Experience.EXPERIENCED -> 12..20
    }

    /** Smaller muscles get a share of the range because big lifts already train them. */
    private val weight = mapOf(
        "chest" to 1.0, "back" to 1.0, "shoulders" to 1.0, "quads" to 1.0, "hamstrings" to 1.0,
        "glutes" to 0.6, "biceps" to 0.7, "triceps" to 0.7, "calves" to 0.6, "core" to 0.5,
    )

    const val FOCUS_BOOST = 1.25
    const val FOCUS_CEILING = 1.3

    fun weightOf(muscle: String): Double = weight[muscle] ?: 0.6

    /** Where in the range a goal sits: 0.0 is the bottom, 1.0 the top. */
    private fun position(goal: TrainingGoal): Double = when (goal) {
        TrainingGoal.BUILD_MUSCLE -> 0.65
        TrainingGoal.LOSE_FAT -> 0.5
        TrainingGoal.GET_STRONGER -> 0.35
        TrainingGoal.GENERAL_FITNESS -> 0.0
        TrainingGoal.ATHLETIC -> 0.3
    }

    /** The lowest and highest weekly hard sets that count as "in range" for this muscle. */
    fun allowed(experience: Experience, muscle: String, focus: Boolean): IntRange {
        val r = range(experience)
        val w = weightOf(muscle)
        val lo = max(2, floor(r.first * w).toInt())
        val hi = ceil(r.last * w * (if (focus) FOCUS_CEILING else 1.0)).toInt()
        return lo..max(lo, hi)
    }

    /** The weekly hard sets the rules ask for, before any session time limit. */
    fun target(experience: Experience, goal: TrainingGoal, muscle: String, focus: Boolean): Int {
        val r = range(experience)
        val base = r.first + (r.last - r.first) * position(goal)
        val raw = (base * weightOf(muscle) * (if (focus) FOCUS_BOOST else 1.0)).roundToInt()
        val a = allowed(experience, muscle, focus)
        return raw.coerceIn(a.first, a.last)
    }

    fun deloadEvery(experience: Experience): Int = when (experience) {
        Experience.NEW -> 6
        Experience.SOME -> 6
        Experience.EXPERIENCED -> 5
    }
}

/**
 * The deterministic training generator (SPEC-v3 "Engines"). Pure Kotlin, no randomness: the same
 * [GeneratorInputs] always give the same [GeneratedProgram]. The baseline and onboarding flows call
 * [generateProgram] directly.
 */
fun generateProgram(inputs: GeneratorInputs): GeneratedProgram = ProgramGenerator(inputs).generate()

/** The split for a number of training days, and the sentence that explains it. */
fun splitFor(days: Int, experience: Experience): Pair<List<DayType>, String> = when (days.coerceIn(2, 6)) {
    2 -> listOf(DayType.FULL, DayType.FULL) to
        "2 days: two full-body sessions, so every muscle is trained twice a week."
    3 -> listOf(DayType.FULL, DayType.FULL, DayType.FULL) to
        "3 days: three full-body sessions, so every muscle is trained three times a week."
    4 -> listOf(DayType.UPPER, DayType.LOWER, DayType.UPPER, DayType.LOWER) to
        "4 days: upper/lower, so each muscle is trained twice a week."
    5 -> if (experience == Experience.NEW) {
        listOf(DayType.UPPER, DayType.LOWER, DayType.FULL, DayType.UPPER, DayType.LOWER) to
            "5 days: upper/lower plus a full-body day. Each muscle gets 3 lighter sessions, which suits a newer lifter."
    } else {
        listOf(DayType.PUSH, DayType.PULL, DayType.LEGS, DayType.UPPER, DayType.LOWER) to
            "5 days: push/pull/legs plus upper/lower, so each muscle is trained twice a week."
    }
    else -> listOf(DayType.PUSH, DayType.PULL, DayType.LEGS, DayType.PUSH, DayType.PULL, DayType.LEGS) to
        "6 days: push/pull/legs twice, so each muscle is trained twice a week."
}

private enum class Role { MAIN, COMPOUND, ISOLATION, EXPLOSIVE }

private data class Scheme(val repsMin: Int, val repsMax: Int, val rpe: Double?, val rest: Int, val timed: Boolean = false)

private class Slot(
    val exercise: CatalogExercise,
    val muscle: String,
    var sets: Int,
    val focus: Boolean,
    val order: Int,
) {
    var role: Role = Role.ISOLATION
    var scheme: Scheme = Scheme(10, 15, 9.0, 60)
}

private class ProgramGenerator(raw: GeneratorInputs) {
    private val inputs = raw.copy(
        daysPerWeek = raw.daysPerWeek.coerceIn(2, 6),
        sessionMinutes = raw.sessionMinutes.coerceIn(20, 180),
        equipment = raw.equipment.ifEmpty { Equipment.BODYWEIGHT_ONLY },
        focusMuscles = raw.focusMuscles.filter { it in Muscles.PROGRAMMED }.toSet(),
    )
    private val exp = inputs.experience
    private val goal = inputs.goal
    private val warnings = mutableListOf<String>()

    /** True when nothing needs weights, so reps run higher. */
    private val bodyweightOnly = inputs.equipment.all { it == Equipment.BODYWEIGHT }
    private val strengthLike = goal == TrainingGoal.GET_STRONGER || goal == TrainingGoal.ATHLETIC

    fun generate(): GeneratedProgram {
        val (dayTypes, splitWhy) = splitFor(inputs.daysPerWeek, exp)
        val fullDays: Map<String, List<Int>> = Muscles.PROGRAMMED.associateWith { m ->
            dayTypes.indices.filter { m in dayTypes[it].muscles }
        }.filterValues { it.isNotEmpty() }

        // Weekly targets, then the per-session shares (never more than a sensible cap in one sitting).
        // A muscle with only a few sets a week is trained on fewer, evenly spaced days, so no
        // session carries a lone set.
        val targets = linkedMapOf<String, Int>()
        val shares = mutableMapOf<String, List<Int>>()
        val activeDays = mutableMapOf<String, List<Int>>()
        for ((muscle, allDays) in fullDays) {
            val focus = muscle in inputs.focusMuscles
            var t = inputs.weeklySetOverrides[muscle]?.coerceIn(0, 40)
                ?: VolumeGuide.target(exp, goal, muscle, focus)
            val cap = (if (focus) 12 else 10) * allDays.size
            if (t > cap) {
                warnings += "${Muscles.label(muscle)}: capped at $cap sets a week so no single session carries too many."
                t = cap
            }
            targets[muscle] = t
            val used = if (t >= 2 * allDays.size || t == 0) allDays else evenlySpaced(allDays, max(1, t / 2))
            activeDays[muscle] = used
            shares[muscle] = splitEvenly(t, used.size)
        }
        val muscleDays: Map<String, List<Int>> = activeDays

        // Candidate exercises per muscle after equipment, experience, hidden and avoid filters.
        val pool = candidatePool()
        val dayLists = dayTypes.indices.map { mutableListOf<Slot>() }
        val missing = mutableListOf<String>()
        var order = 0
        for ((muscle, days) in muscleDays) {
            val focus = muscle in inputs.focusMuscles
            days.forEachIndexed { occurrence, dayIndex ->
                val n = shares.getValue(muscle)[occurrence]
                if (n <= 0) return@forEachIndexed
                val picks = pickExercises(muscle, n, occurrence, pool, dayLists[dayIndex].map { it.exercise.id }.toSet())
                if (picks.isEmpty()) {
                    if (muscle !in missing) missing += muscle
                    return@forEachIndexed
                }
                val setSplit = splitEvenly(n, picks.size)
                picks.forEachIndexed { i, ex -> dayLists[dayIndex] += Slot(ex, muscle, setSplit[i], focus, order++) }
            }
        }
        if (missing.isNotEmpty()) {
            warnings += "No " + missing.joinToString(", ") +
                " exercise fits your equipment and avoid choices, so " +
                (if (missing.size == 1) "it was" else "they were") + " left out."
        }

        val days = dayTypes.mapIndexed { index, type ->
            val sameType = dayTypes.count { it == type }
            val nth = dayTypes.take(index + 1).count { it == type }
            val name = if (sameType > 1) "${type.label} ${'A' + (nth - 1)}" else type.label
            buildDay(name, type, nth - 1, dayLists[index], pool)
        }

        val weekly = linkedMapOf<String, Int>()
        days.flatMap { it.exercises }.filter { !it.explosive }.forEach { weekly.merge(it.muscle, it.sets, Int::plus) }
        val ordered = Muscles.PROGRAMMED.filter { it in weekly }.associateWith { weekly.getValue(it) }

        val trimmed = targets.filter { (m, t) -> t > 0 && (ordered[m] ?: 0) < t * 0.85 && m !in missing }
        if (trimmed.isNotEmpty()) {
            warnings += "${inputs.sessionMinutes}-minute sessions only fit " +
                trimmed.keys.joinToString(", ") { "${ordered[it] ?: 0} sets of $it" } +
                " a week, under the usual target. Longer sessions or more days would fit more."
        }

        val deload = (inputs.deloadEveryWeeks ?: VolumeGuide.deloadEvery(exp)).coerceIn(3, 12)
        return GeneratedProgram(
            name = "${goal.label}, ${inputs.daysPerWeek} days",
            description = describe(deload),
            days = days,
            weeklySets = ordered,
            targetSets = targets.filterValues { it > 0 },
            deloadEveryWeeks = deload,
            whys = programWhys(splitWhy, ordered, deload),
            warnings = warnings.toList(),
        )
    }

    // ------------------------------------------------------------ selection

    private class Pool(val compounds: Map<String, List<CatalogExercise>>, val isolations: Map<String, List<CatalogExercise>>, val explosive: List<CatalogExercise>)

    private fun candidatePool(): Pool {
        val usable = ExerciseCatalog.ALL.filter { ex ->
            ex.equipment in inputs.equipment &&
                ex.minExperience.ordinal <= exp.ordinal &&
                ex.id !in inputs.hiddenExerciseIds &&
                ex.loads.none { it in inputs.avoid }
        }
        val regular = usable.filter { !it.explosive }
        return Pool(
            compounds = regular.filter { it.compound }.groupBy { it.primaryMuscle },
            isolations = regular.filter { !it.compound }.groupBy { it.primaryMuscle }
                .mapValues { (muscle, list) -> if (muscle == "shoulders") interleaveRearDelts(list) else list },
            explosive = usable.filter { it.explosive },
        )
    }

    /** Lateral raises first, then rear-delt work, alternating, so the second isolation is a rear delt. */
    private fun interleaveRearDelts(list: List<CatalogExercise>): List<CatalogExercise> {
        val (lateral, rear) = list.partition { it.name.contains("lateral", ignoreCase = true) }
        val out = mutableListOf<CatalogExercise>()
        for (i in 0 until max(lateral.size, rear.size)) {
            lateral.getOrNull(i)?.let { out += it }
            rear.getOrNull(i)?.let { out += it }
        }
        return out
    }

    private fun pickExercises(
        muscle: String,
        sets: Int,
        occurrence: Int,
        pool: Pool,
        taken: Set<String>,
    ): List<CatalogExercise> {
        val compounds = pool.compounds[muscle].orEmpty().filter { it.id !in taken }
        val isolations = pool.isolations[muscle].orEmpty().filter { it.id !in taken }
        val leadsWithCompound = muscle in COMPOUND_MUSCLES
        var wanted = when {
            sets <= 3 -> 1
            sets <= 6 -> 2
            else -> 3
        }
        if (exp == Experience.NEW) wanted = min(wanted, 2)

        val picks = mutableListOf<CatalogExercise>()
        if (leadsWithCompound && compounds.isNotEmpty()) picks += compounds[occurrence % compounds.size]
        // Arms, calves and core are isolation work; bodyweight-only programs fall back to compounds.
        val source = if (isolations.isNotEmpty()) isolations else compounds
        var j = 0
        while (picks.size < wanted && source.isNotEmpty() && j < source.size + wanted) {
            val candidate = source[(occurrence + j) % source.size]
            if (picks.none { it.id == candidate.id }) picks += candidate
            j++
        }
        if (picks.isEmpty() && compounds.isNotEmpty()) picks += compounds[occurrence % compounds.size]
        return picks
    }

    // --------------------------------------------------------------- a day

    private fun buildDay(name: String, type: DayType, nth: Int, slots: MutableList<Slot>, pool: Pool): GeneratedDay {
        slots.sortWith(
            compareByDescending<Slot> { it.exercise.compound }
                .thenByDescending { it.focus }
                .thenBy { Muscles.PROGRAMMED.indexOf(it.muscle) }
                .thenBy { it.order },
        )
        // Power work goes first, while the legs and arms are fresh.
        val explosiveSlots = if (goal == TrainingGoal.ATHLETIC) explosiveFor(type, nth, pool) else emptyList()
        val all = (explosiveSlots + slots).toMutableList()

        var mains = 0
        for (slot in all) {
            slot.role = when {
                slot.exercise.explosive -> Role.EXPLOSIVE
                slot.exercise.compound && strengthLike && mains < 2 && slot.muscle in COMPOUND_MUSCLES -> {
                    mains++
                    Role.MAIN
                }
                slot.exercise.compound && slot.muscle in COMPOUND_MUSCLES -> Role.COMPOUND
                else -> Role.ISOLATION
            }
            slot.scheme = schemeFor(slot)
        }

        fit(all)

        val exercises = all.map { toExercise(it) }
        val minutes = minutesOf(all)
        val muscles = exercises.map { it.muscle }.distinct()
        val why = "${type.label} day: " + muscles.joinToString(", ") +
            ". Compound lifts come first while you are fresh, isolation work after. About $minutes minutes."
        return GeneratedDay(name, exercises, minutes, why)
    }

    private fun explosiveFor(type: DayType, nth: Int, pool: Pool): List<Slot> {
        val lower = type in setOf(DayType.FULL, DayType.LOWER, DayType.LEGS)
        val upper = type in setOf(DayType.UPPER, DayType.PUSH)
        val candidates = pool.explosive.filter { ex ->
            if (lower) ex.primaryMuscle in setOf("quads", "hamstrings") || ex.name == "Power clean"
            else if (upper) ex.primaryMuscle == "chest"
            else false
        }
        if (candidates.isEmpty()) return emptyList()
        val pick = candidates[nth % candidates.size]
        return listOf(Slot(pick, pick.primaryMuscle, 3, focus = false, order = -1))
    }

    private fun schemeFor(slot: Slot): Scheme {
        val ex = slot.exercise
        if (ex.name == "Plank" || ex.name == "Farmer's carry") {
            return Scheme(30, 60, null, 60, timed = true)
        }
        val coreOrCalf = slot.muscle == "calves" || slot.muscle == "core"
        val base = when (slot.role) {
            Role.EXPLOSIVE -> return Scheme(3, 5, null, ex.defaultRestSec)
            Role.MAIN -> when {
                exp == Experience.NEW -> Scheme(5, 6, 7.0, 180)
                goal == TrainingGoal.ATHLETIC -> Scheme(3, 5, 7.0, 150)
                else -> Scheme(3, 5, 8.0, 180)
            }
            Role.COMPOUND -> when (goal) {
                TrainingGoal.GET_STRONGER -> Scheme(6, 8, 8.0, 120)
                TrainingGoal.ATHLETIC -> Scheme(6, 8, 7.0, 120)
                TrainingGoal.GENERAL_FITNESS -> Scheme(8, 12, 7.0, 90)
                else -> Scheme(6, 10, 8.0, 120)
            }
            Role.ISOLATION -> when (goal) {
                TrainingGoal.GET_STRONGER -> Scheme(10, 12, 8.0, 75)
                TrainingGoal.ATHLETIC -> Scheme(8, 12, 8.0, 75)
                TrainingGoal.GENERAL_FITNESS -> if (coreOrCalf) Scheme(12, 20, 8.0, 60) else Scheme(12, 15, 8.0, 60)
                else -> if (coreOrCalf) Scheme(12, 20, 9.0, 60) else Scheme(10, 15, 9.0, 60)
            }
        }
        var scheme = base
        if (bodyweightOnly) {
            scheme = when (slot.role) {
                Role.ISOLATION -> scheme.copy(repsMin = 12, repsMax = 20)
                else -> scheme.copy(repsMin = 8, repsMax = 15)
            }
        }
        // Advanced overrides: keep the main lifts of a strength program heavy.
        val protectMain = strengthLike && slot.role == Role.MAIN
        if (!protectMain) {
            if (slot.role == Role.ISOLATION) inputs.isolationReps?.let { scheme = scheme.copy(repsMin = it.min, repsMax = it.max) }
            else inputs.compoundReps?.let { scheme = scheme.copy(repsMin = it.min, repsMax = it.max) }
        }
        return scheme
    }

    private fun workSeconds(slot: Slot): Int {
        val s = slot.scheme
        return if (s.timed) 45 else ((s.repsMin + s.repsMax) / 2.0 * 3 + 15).roundToInt()
    }

    private fun minutesOf(slots: List<Slot>): Int = estimateMinutes(
        slots.map { Triple(it.sets, workSeconds(it), it.scheme.rest) },
    )

    /**
     * Trims a day until it fits the time budget: first sets above three (non-focus isolation work
     * first), then whole small isolation exercises, then sets down to two, then any exercise but the
     * main lifts. Focus muscles are always the last to lose anything.
     */
    private fun fit(slots: MutableList<Slot>) {
        val budget = inputs.sessionMinutes
        fun bySetsFirst(minSets: Int) = slots.filter { it.sets > minSets && it.role != Role.EXPLOSIVE }.minWithOrNull(
            compareBy<Slot>({ it.focus }, { it.role == Role.MAIN || it.role == Role.COMPOUND }, { -it.sets }, { -it.order }),
        )
        while (minutesOf(slots) > budget) {
            bySetsFirst(3)?.let { it.sets--; return@let true } ?: run {
                val smallIso = slots.filter { it.role == Role.ISOLATION && !it.focus }
                    .minWithOrNull(compareBy<Slot>({ it.sets }, { -it.order }))
                if (smallIso != null && slots.size > 1) {
                    slots.remove(smallIso)
                } else {
                    val s2 = bySetsFirst(2)
                    if (s2 != null) {
                        s2.sets--
                    } else {
                        val removable = slots.filter { it.role != Role.MAIN }
                        if (removable.isEmpty() || slots.size <= 1) return
                        val drop = removable.minWithOrNull(
                            compareBy<Slot>({ it.role == Role.EXPLOSIVE }, { it.focus }, { it.role == Role.COMPOUND }, { -it.order }),
                        )!!
                        slots.remove(drop)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ the words

    private fun progressionFor(slot: Slot): ProgressionType? {
        val ex = slot.exercise
        return when {
            slot.role == Role.EXPLOSIVE || slot.scheme.timed -> null
            ex.isBodyweight -> ProgressionType.DOUBLE_PROGRESSION
            strengthLike && (slot.role == Role.MAIN || slot.role == Role.COMPOUND) -> when (exp) {
                Experience.NEW -> ProgressionType.LINEAR
                Experience.SOME -> ProgressionType.DOUBLE_PROGRESSION
                Experience.EXPERIENCED -> ProgressionType.RPE_BASED
            }
            (slot.role == Role.MAIN || slot.role == Role.COMPOUND) && exp == Experience.NEW -> ProgressionType.LINEAR
            else -> ProgressionType.DOUBLE_PROGRESSION
        }
    }

    private fun toExercise(slot: Slot): GeneratedExercise {
        val ex = slot.exercise
        val s = slot.scheme
        val progression = progressionFor(slot)
        val lead = when (slot.role) {
            Role.MAIN -> "Main lift, done first while you are freshest."
            Role.COMPOUND -> {
                val also = ex.secondaryMuscles.take(2)
                "Compound lift: trains ${slot.muscle}" + (if (also.isEmpty()) "" else " and ${also.joinToString(" and ")}") + " together."
            }
            Role.ISOLATION -> "Isolation work for direct ${slot.muscle} volume."
            Role.EXPLOSIVE -> "Fast, low-rep power work, kept short so every rep stays sharp."
        }
        val reps = when {
            s.timed -> "Hold ${s.repsMin} to ${s.repsMax} seconds."
            s.repsMin == s.repsMax -> "${s.repsMin} reps."
            else -> "${s.repsMin} to ${s.repsMax} reps."
        }
        val effort = s.rpe?.let { " Effort ${formatRpe(it)} of 10." } ?: ""
        val how = when (progression) {
            ProgressionType.LINEAR -> " Adds weight when you hit all your reps."
            ProgressionType.DOUBLE_PROGRESSION -> " Works up the rep range, then adds weight."
            ProgressionType.RPE_BASED -> " Weight follows how hard the last session felt."
            ProgressionType.PERCENTAGE_1RM -> " Weight is a percentage of your estimated max."
            ProgressionType.CUSTOM, null -> ""
        }
        return GeneratedExercise(
            exerciseId = ex.id,
            name = ex.name,
            muscle = slot.muscle,
            sets = slot.sets,
            repsMin = s.repsMin,
            repsMax = s.repsMax,
            rpe = s.rpe,
            restSec = s.rest,
            progression = progression,
            why = "$lead $reps$effort$how",
            explosive = slot.role == Role.EXPLOSIVE,
        )
    }

    private fun programWhys(splitWhy: String, weekly: Map<String, Int>, deload: Int): List<String> {
        val out = mutableListOf(splitWhy)
        val r = VolumeGuide.range(exp)
        val big = weekly.filterKeys { VolumeGuide.weightOf(it) >= 1.0 }.values
        out += "${exp.label} lifter: about ${r.first} to ${r.last} hard sets per muscle per week" +
            (if (big.isNotEmpty()) ", and yours land at ${big.min()} to ${big.max()} for the big muscles." else ".") +
            " Smaller muscles get a share, since the big lifts train them too."
        out += when (goal) {
            TrainingGoal.BUILD_MUSCLE -> "Build muscle: 6 to 10 reps on compounds and 10 to 15 on isolations, effort 8 to 9 of 10."
            TrainingGoal.LOSE_FAT -> "Lose fat, keep muscle: the same rep ranges as building muscle, because heavy training is what tells your body to keep it."
            TrainingGoal.GET_STRONGER -> "Get stronger: 3 to 6 reps on the main lifts at effort 7 to 8, with lighter accessory work behind them."
            TrainingGoal.GENERAL_FITNESS -> "General fitness: moderate reps (8 to 15) at a comfortable effort of 7 to 8, so it is easy to keep up."
            TrainingGoal.ATHLETIC -> "Athletic: fast low-rep jumps and lifts first, then strength work on the main lifts."
        }
        out += progressionWhy()
        out += "Deload every $deload weeks: same exercises, about half the sets and 10% less weight, to shed fatigue."
        if (inputs.focusMuscles.isNotEmpty()) {
            out += "Focus on " + inputs.focusMuscles.sorted().joinToString(", ") +
                ": about 25% more sets, placed early in the session."
        }
        if (inputs.avoid.isNotEmpty()) {
            out += "Left out exercises that load your " + inputs.avoid.sortedBy { it.ordinal }.joinToString(", ") { it.label.lowercase() } + "."
        }
        out += "Sessions are capped at ${inputs.sessionMinutes} minutes, counting sets, reps and rest."
        return out
    }

    private fun progressionWhy(): String = when {
        strengthLike && exp == Experience.EXPERIENCED -> "Progression: main lifts follow your logged effort (RPE based); accessories work up the rep range, then add weight."
        strengthLike && exp == Experience.NEW -> "Progression: add weight whenever you hit every rep on the main lifts; accessories work up the rep range first."
        exp == Experience.NEW -> "Progression: compounds add weight when you hit all reps (new lifters can add weight every session); isolations work up the rep range first."
        else -> "Progression: work up the rep range, then add weight (double progression), which suits steady muscle gain."
    }

    private fun describe(deload: Int): String = buildString {
        append("Made by Intake for: ${goal.label.lowercase()}, ${exp.label.lowercase()} lifter, ")
        append("${inputs.daysPerWeek} days a week, about ${inputs.sessionMinutes} minutes. ")
        append("Every number can be edited. ")
        append("Deload: every $deload weeks (weeks $deload, ${deload * 2}, ${deload * 3} ...) keep the same exercises, do about half the sets ")
        append("and use about 10% less weight, stopping well short of failure.")
    }

    private companion object {
        val COMPOUND_MUSCLES = setOf("chest", "back", "shoulders", "quads", "hamstrings", "glutes")

        fun formatRpe(rpe: Double): String =
            if (rpe == rpe.toInt().toDouble()) rpe.toInt().toString() else rpe.toString()
    }
}

/** Picks [count] entries spread evenly through [days]. */
internal fun evenlySpaced(days: List<Int>, count: Int): List<Int> =
    if (count >= days.size) days else (0 until count).map { days[(it * days.size) / count] }

/** Splits [total] into [parts] shares that differ by at most one, larger shares first. */
internal fun splitEvenly(total: Int, parts: Int): List<Int> {
    if (parts <= 0) return emptyList()
    val base = total / parts
    val extra = total % parts
    return List(parts) { base + if (it < extra) 1 else 0 }
}

/** Session length: a fixed warm-up and changeover allowance plus sets x (work + rest). */
fun estimateMinutes(sets: List<Triple<Int, Int, Int>>): Int {
    val seconds = sets.sumOf { (n, work, rest) -> n * (work + rest) }
    return WARMUP_MINUTES + ceil(seconds / 60.0).toInt()
}

const val WARMUP_MINUTES = 5
