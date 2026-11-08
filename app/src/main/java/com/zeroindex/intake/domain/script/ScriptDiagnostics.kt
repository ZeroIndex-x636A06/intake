package com.zeroindex.intake.domain.script

enum class Severity { ERROR, WARNING }

/**
 * Every diagnostic the Intake script parsers can emit. [slug] is the heading
 * of its entry in docs/scripting/errors.md; [anchor] is the "page#section" of
 * the wiki page that explains the rule, so the app can link to the right place.
 */
enum class DiagnosticCode(val severity: Severity, val anchor: String) {
    // General
    NOT_AN_INTAKE_SCRIPT(Severity.ERROR, "README#file-kinds"),
    FILE_TOO_LARGE(Severity.ERROR, "README#limits"),
    UNCLOSED_COMMENT(Severity.ERROR, "README#comments"),
    TOO_MANY_PROBLEMS(Severity.ERROR, "README#limits"),
    UNEXPECTED_HEADING(Severity.ERROR, "README#headings"),
    UNKNOWN_KEY(Severity.ERROR, "README#conventions"),
    MISSING_COLON(Severity.ERROR, "README#conventions"),
    DUPLICATE_KEY(Severity.ERROR, "README#conventions"),
    EMPTY_VALUE(Severity.ERROR, "README#conventions"),
    UNEXPECTED_TEXT(Severity.ERROR, "README#conventions"),

    // Program
    MISSING_PROGRAM_NAME(Severity.ERROR, "program#header-keys"),
    UNKNOWN_UNITS(Severity.ERROR, "program#header-keys"),
    BAD_REST(Severity.ERROR, "program#rest"),
    NO_DAYS(Severity.ERROR, "program#days"),
    EMPTY_DAY(Severity.WARNING, "program#days"),
    DUPLICATE_DAY(Severity.WARNING, "program#days"),
    EXERCISE_OUTSIDE_DAY(Severity.ERROR, "program#exercises"),
    FIELD_OUTSIDE_EXERCISE(Severity.ERROR, "program#exercises"),
    EMPTY_EXERCISE_NAME(Severity.ERROR, "program#exercises"),
    MISSING_SETS(Severity.ERROR, "program#exercise-fields"),
    MISSING_REPS(Severity.ERROR, "program#exercise-fields"),
    BAD_SETS(Severity.ERROR, "program#sets"),
    BAD_REPS(Severity.ERROR, "program#reps"),
    BAD_RPE(Severity.ERROR, "program#rpe"),
    BAD_WEIGHT(Severity.ERROR, "program#weight"),
    BAD_RULE(Severity.ERROR, "progress-rules#writing-a-rule"),
    RULE_NEEDS_INCREMENT(Severity.ERROR, "progress-rules#increments-and-units"),
    BAD_INCREMENT(Severity.ERROR, "progress-rules#increments-and-units"),
    BAD_DELOAD(Severity.ERROR, "progress-rules#linear"),
    BAD_PERCENT(Severity.ERROR, "progress-rules#percent-of-max"),
    BAD_EXPRESSION(Severity.ERROR, "progress-rules#custom"),
    RPE_RULE_NEEDS_RPE(Severity.ERROR, "progress-rules#rpe-based"),
    RPE_RULE_WITHOUT_RPE(Severity.WARNING, "progress-rules#rpe-based"),
    AMRAP_WITH_DOUBLE(Severity.WARNING, "progress-rules#double-progression"),

    // Meal plan
    MISSING_PLAN_NAME(Severity.ERROR, "meal-plan#header"),
    UNKNOWN_SECTION(Severity.ERROR, "meal-plan#sections"),
    NO_WEEKS(Severity.ERROR, "meal-plan#weeks"),
    BAD_WEEK_HEADING(Severity.ERROR, "meal-plan#weeks"),
    BAD_WEEK_NUMBER(Severity.ERROR, "meal-plan#weeks"),
    DUPLICATE_WEEK(Severity.ERROR, "meal-plan#weeks"),
    BAD_SERVINGS(Severity.ERROR, "meal-plan#week-fields"),
    BAD_TIME(Severity.ERROR, "meal-plan#week-fields"),
    INGREDIENT_NO_NAME(Severity.ERROR, "meal-plan#ingredients"),
    BAD_INGREDIENT_WEIGHT(Severity.ERROR, "meal-plan#ingredient-fields"),
    BAD_NUTRITION(Severity.ERROR, "meal-plan#nutrition"),
    NUTRITION_IMPLAUSIBLE(Severity.ERROR, "meal-plan#nutrition"),
    INCOMPLETE_NUTRITION(Severity.WARNING, "meal-plan#nutrition"),
    INCOMPLETE_MACROS(Severity.WARNING, "meal-plan#nutrition"),
    DUPLICATE_RECIPE(Severity.ERROR, "meal-plan#extra-recipes"),
    SHOPPING_UNKNOWN_CATEGORY(Severity.WARNING, "meal-plan#shopping"),
    SHOPPING_NO_CATEGORY(Severity.WARNING, "meal-plan#shopping"),
    VENUE_NO_ORDERS(Severity.ERROR, "meal-plan#eat-out"),
    BAD_WEEKS_LIST(Severity.ERROR, "meal-plan#eat-out-orders"),
    BAD_ESTIMATE(Severity.ERROR, "meal-plan#eat-out-orders"),
    ;

    /** Heading in errors.md, e.g. "missing-sets". */
    val slug: String get() = name.lowercase().replace('_', '-')
    val errorsAnchor: String get() = "errors#$slug"
}

data class Diagnostic(
    val severity: Severity,
    val line: Int,
    val column: Int,
    val code: DiagnosticCode,
    val message: String,
) {
    /** "page#section" of the wiki page that explains the rule. */
    val anchor: String get() = code.anchor

    override fun toString(): String =
        "${severity.name} line $line, column $column: $message [${code.errorsAnchor}]"
}

/** Document (null when there were errors) plus every diagnostic found. */
data class ScriptResult<out T>(val document: T?, val diagnostics: List<Diagnostic>) {
    val errors: List<Diagnostic> get() = diagnostics.filter { it.severity == Severity.ERROR }
    val warnings: List<Diagnostic> get() = diagnostics.filter { it.severity == Severity.WARNING }
    val isOk: Boolean get() = document != null
}

/** Collects diagnostics, capping the flood from a file that is not a script at all. */
internal class DiagnosticSink(private val limit: Int = 100) {
    private val items = ArrayList<Diagnostic>()
    var capped = false
        private set
    val hasErrors: Boolean get() = items.any { it.severity == Severity.ERROR }

    fun report(code: DiagnosticCode, line: Int, column: Int, message: String) {
        if (items.size >= limit) {
            if (!capped) {
                capped = true
                items += Diagnostic(
                    Severity.ERROR, line, 1, DiagnosticCode.TOO_MANY_PROBLEMS,
                    "Too many problems; the rest are not shown. Fix these first and import again.",
                )
            }
            return
        }
        items += Diagnostic(code.severity, line, column.coerceAtLeast(1), code, message)
    }

    fun sorted(): List<Diagnostic> = items.sortedWith(compareBy({ it.line }, { it.column }))
}
