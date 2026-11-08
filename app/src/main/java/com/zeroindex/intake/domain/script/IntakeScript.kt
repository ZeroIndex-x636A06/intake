package com.zeroindex.intake.domain.script

enum class ScriptKind { PROGRAM, MEAL_PLAN }

sealed interface ScriptDocument {
    data class Program(val doc: ProgramDoc) : ScriptDocument
    data class MealPlan(val doc: MealPlanDoc) : ScriptDocument
}

/** Entry point: works out which kind of file this is and parses it. Never throws. */
object IntakeScript {

    /** The kind named by the first non-blank line, or null when it is neither. */
    fun detect(text: String): ScriptKind? {
        val first = text.removePrefix("\uFEFF").lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val m = Regex("""(?i)^\s{0,3}#\s*(program|meal\s*plan)\s*:""").find(first) ?: return null
        return if (m.groupValues[1].lowercase().startsWith("program")) ScriptKind.PROGRAM else ScriptKind.MEAL_PLAN
    }

    fun parse(text: String): ScriptResult<ScriptDocument> = try {
        when (detect(text)) {
            ScriptKind.PROGRAM -> ProgramScript.parse(text).let { r ->
                ScriptResult(r.document?.let { ScriptDocument.Program(it) }, r.diagnostics)
            }
            ScriptKind.MEAL_PLAN -> MealPlanScript.parse(text).let { r ->
                ScriptResult(r.document?.let { ScriptDocument.MealPlan(it) }, r.diagnostics)
            }
            null -> {
                val line = text.lineSequence().indexOfFirst { it.isNotBlank() }.coerceAtLeast(0) + 1
                ScriptResult(
                    null,
                    listOf(
                        Diagnostic(
                            Severity.ERROR, line, 1, DiagnosticCode.NOT_AN_INTAKE_SCRIPT,
                            "This is not an Intake script. The first line must be \"# Program: <name>\" or \"# Meal plan: <name>\".",
                        ),
                    ),
                )
            }
        }
    } catch (e: Exception) {
        ScriptResult(
            null,
            listOf(Diagnostic(Severity.ERROR, 1, 1, DiagnosticCode.NOT_AN_INTAKE_SCRIPT, "This file could not be read.")),
        )
    }

    fun print(doc: ScriptDocument): String = when (doc) {
        is ScriptDocument.Program -> ProgramScript.print(doc.doc)
        is ScriptDocument.MealPlan -> MealPlanScript.print(doc.doc)
    }
}
