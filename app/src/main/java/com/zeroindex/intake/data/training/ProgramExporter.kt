package com.zeroindex.intake.data.training

import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgressionRuleEntity
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.script.DayDoc
import com.zeroindex.intake.domain.script.ExerciseDoc
import com.zeroindex.intake.domain.script.Mass
import com.zeroindex.intake.domain.script.ProgramDoc
import com.zeroindex.intake.domain.script.Reps
import com.zeroindex.intake.domain.script.RuleSpec
import com.zeroindex.intake.domain.script.ScriptWeightUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The live rows of one program, as the exporter needs them. */
data class ProgramExportRows(
    val program: ProgramEntity,
    val days: List<ProgramDayEntity>,
    val exercisesByDay: Map<String, List<ProgramExerciseEntity>>,
    val exercises: Map<String, ExerciseEntity>,
    val rules: Map<String, ProgressionRuleEntity>,
)

/**
 * The inverse of [ProgramImportBuilder]: rows back into a [ProgramDoc]. Pure. Everything is
 * written out explicitly (rest and rule on every exercise, in kilograms), so the exported
 * file means the same thing whatever the program-level defaults were in the file it came from.
 */
object ProgramExporter {

    fun toDoc(rows: ProgramExportRows): ProgramDoc = ProgramDoc(
        name = rows.program.name,
        units = ScriptWeightUnit.KG,
        notes = rows.program.description.lines().map { it.trim() }.filter { it.isNotEmpty() },
        days = rows.days.sortedBy { it.dayIndex }.map { day ->
            DayDoc(
                name = day.name,
                exercises = rows.exercisesByDay[day.id].orEmpty().sortedBy { it.displayOrder }.mapNotNull { pe ->
                    val ex = rows.exercises[pe.exerciseId] ?: return@mapNotNull null
                    ExerciseDoc(
                        name = ex.name,
                        sets = pe.targetSets,
                        reps = repsOf(pe.targetRepsMin, pe.targetRepsMax),
                        rpe = pe.targetRpe,
                        weight = pe.startWeightKg?.let { Mass(it, ScriptWeightUnit.KG) },
                        bodyweight = ex.isBodyweight,
                        restSec = pe.restSec,
                        progress = pe.progressionRuleId?.let { rows.rules[it] }?.let { specOf(it) } ?: RuleSpec.NoProgression,
                    )
                },
            )
        },
    )

    fun repsOf(min: Int, max: Int): Reps = when {
        min == 1 && max >= Reps.AMRAP_ENTITY_MAX -> Reps.Amrap
        min == max -> Reps.Fixed(min)
        else -> Reps.Range(min, max)
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Rule row back to a spec; null when the stored params cannot be read. */
    fun specOf(rule: ProgressionRuleEntity): RuleSpec? {
        val obj = runCatching { json.parseToJsonElement(rule.params) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        fun num(key: String): Double? = runCatching { obj[key]?.jsonPrimitive?.content?.toDoubleOrNull() }.getOrNull()
        fun str(key: String): String? = runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull()
        fun kg(v: Double) = Mass(v, ScriptWeightUnit.KG)
        return when (rule.type) {
            ProgressionType.LINEAR -> RuleSpec.Linear(
                increment = kg(num("incrementKg") ?: 2.5),
                deloadPercent = num("deloadPercent"),
                deloadAfter = num("failuresBeforeDeload")?.toInt(),
            )
            ProgressionType.DOUBLE_PROGRESSION -> RuleSpec.DoubleProgression(kg(num("incrementKg") ?: 2.5))
            ProgressionType.RPE_BASED -> RuleSpec.RpeBased
            ProgressionType.PERCENTAGE_1RM -> RuleSpec.PercentOf1rm(num("percentageOf1rm") ?: 75.0)
            ProgressionType.CUSTOM -> str("expression")?.takeIf { it.isNotBlank() }?.let { RuleSpec.Custom(it) }
        }
    }

    fun fileName(programName: String): String {
        val base = programName.trim().replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "").replace(Regex("""\s+"""), " ").trim()
        return (base.ifEmpty { "program" }) + ".md"
    }
}
