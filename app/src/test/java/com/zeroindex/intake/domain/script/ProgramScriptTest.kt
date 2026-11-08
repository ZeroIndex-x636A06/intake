package com.zeroindex.intake.domain.script

import com.zeroindex.intake.domain.model.ProgressionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramScriptTest {

    private fun parse(text: String) = ProgramScript.parse(text.trimIndent())

    private fun doc(text: String): ProgramDoc {
        val r = parse(text)
        assertTrue("unexpected diagnostics: ${r.diagnostics}", r.errors.isEmpty())
        return r.document!!
    }

    private fun exercise(fields: String, header: String = ""): ExerciseDoc =
        doc("# Program: T\n$header\n## D\n### X\n$fields").days.single().exercises.single()

    private fun firstError(text: String): Diagnostic {
        val r = parse(text)
        assertNull(r.document)
        return r.errors.first()
    }

    /** The error with [code], wherever it sits in the list. */
    private fun error(text: String, code: DiagnosticCode): Diagnostic {
        val r = parse(text)
        assertNull(r.document)
        return r.errors.firstOrNull { it.code == code } ?: throw AssertionError("no $code in ${r.diagnostics}")
    }

    @Test fun `the canonical example parses`() {
        val p = doc(
            """
            # Program: Strength Block A
            units: lb
            rest: 2:00
            progress: double progression, add 5 lb

            ## Upper A

            ### Bench Press
            sets: 4
            reps: 6-8
            rpe: 8
            weight: 185
            rest: 2:30

            ### Overhead Press
            sets: 3
            reps: 5
            progress: linear, add 5 lb

            ### Pull-up
            sets: 3
            reps: max
            """,
        )
        assertEquals("Strength Block A", p.name)
        assertEquals(ScriptWeightUnit.LB, p.units)
        assertEquals(120, p.restSec)
        assertEquals(RuleSpec.DoubleProgression(Mass(5.0, ScriptWeightUnit.LB)), p.progress)
        val ex = p.days.single().exercises
        assertEquals(listOf("Bench Press", "Overhead Press", "Pull-up"), ex.map { it.name })
        assertEquals(Reps.Range(6, 8), ex[0].reps)
        assertEquals(8.0, ex[0].rpe!!, 0.0)
        assertEquals(Mass(185.0, ScriptWeightUnit.LB), ex[0].weight)
        assertEquals(150, p.effectiveRest(ex[0]))
        assertEquals(120, p.effectiveRest(ex[1]))
        assertEquals(RuleSpec.Linear(Mass(5.0, ScriptWeightUnit.LB)), ex[1].progress)
        assertEquals(p.progress, p.effectiveProgress(ex[2]))
        assertEquals(Reps.Amrap, ex[2].reps)
    }

    @Test fun `field order case and spacing do not matter`() {
        val a = exercise("sets: 4\nreps: 6-8\nrpe: 8\nweight: 185 lb\nrest: 2:30", "units: lb")
        val b = exercise("  WEIGHT :185LB\n  Rest:   2:30\nRPE: 8\nREPS : 6 - 8\nSets:4", "UNITS: LB")
        assertEquals(a, b)
    }

    @Test fun `header keys may come in any order and rules resolve against the units`() {
        val p = doc("# Program: T\nprogress: linear, add 5\nunits: lb\n## D\n### X\nsets: 1\nreps: 1")
        assertEquals(RuleSpec.Linear(Mass(5.0, ScriptWeightUnit.LB)), p.progress)
    }

    @Test fun `reps forms`() {
        assertEquals(Reps.Range(6, 8), exercise("sets: 3\nreps: 6-8").reps)
        assertEquals(Reps.Range(6, 8), exercise("sets: 3\nreps: 6 - 8").reps)
        assertEquals(Reps.Range(6, 8), exercise("sets: 3\nreps: 6 to 8").reps)
        assertEquals(Reps.Range(6, 8), exercise("sets: 3\nreps: 6–8 reps").reps)
        assertEquals(Reps.Fixed(8), exercise("sets: 3\nreps: 8").reps)
        assertEquals(Reps.Fixed(8), exercise("sets: 3\nreps: 8-8").reps)
        assertEquals(Reps.Amrap, exercise("sets: 3\nreps: max").reps)
        assertEquals(Reps.Amrap, exercise("sets: 3\nreps: AMRAP").reps)
        assertEquals(1, Reps.Amrap.entityMin)
        assertEquals(Reps.AMRAP_ENTITY_MAX, Reps.Amrap.entityMax)
    }

    @Test fun `weight forms use the program units when none is given`() {
        assertEquals(Mass(185.0, ScriptWeightUnit.LB), exercise("sets: 1\nreps: 1\nweight: 185", "units: lb").weight)
        assertEquals(Mass(185.0, ScriptWeightUnit.KG), exercise("sets: 1\nreps: 1\nweight: 185").weight)
        assertEquals(Mass(185.0, ScriptWeightUnit.LB), exercise("sets: 1\nreps: 1\nweight: 185lbs").weight)
        assertEquals(Mass(80.0, ScriptWeightUnit.KG), exercise("sets: 1\nreps: 1\nweight: 80 kg", "units: lb").weight)
        assertEquals(Mass(62.5, ScriptWeightUnit.KG), exercise("sets: 1\nreps: 1\nweight: 62.5").weight)
        val bw = exercise("sets: 1\nreps: 1\nweight: bw")
        assertTrue(bw.bodyweight)
        assertNull(bw.weight)
        assertTrue(exercise("sets: 1\nreps: 1\nweight: Bodyweight").bodyweight)
        val added = exercise("sets: 1\nreps: 1\nweight: bodyweight + 25 lb")
        assertTrue(added.bodyweight)
        assertEquals(Mass(25.0, ScriptWeightUnit.LB), added.weight)
    }

    @Test fun `weights convert to kilograms`() {
        assertEquals(83.9146, Mass(185.0, ScriptWeightUnit.LB).kg, 0.001)
        assertEquals(80.0, Mass(80.0, ScriptWeightUnit.KG).kg, 0.0)
    }

    @Test fun `rest forms`() {
        fun rest(v: String) = exercise("sets: 1\nreps: 1\nrest: $v").restSec
        assertEquals(150, rest("2:30"))
        assertEquals(150, rest("150s"))
        assertEquals(150, rest("150 sec"))
        assertEquals(120, rest("2 min"))
        assertEquals(150, rest("2 min 30 s"))
        assertEquals(90, rest("90 seconds"))
        assertEquals(90, rest("1 minute 30 seconds"))
        assertEquals(60, rest("1m"))
    }

    @Test fun `rule forms`() {
        fun rule(v: String, header: String = "units: lb") =
            exercise("sets: 1\nreps: 1\nrpe: 8\nprogress: $v", header).progress
        val lb5 = Mass(5.0, ScriptWeightUnit.LB)
        assertEquals(RuleSpec.Linear(lb5), rule("linear, add 5 lb"))
        assertEquals(RuleSpec.Linear(lb5), rule("linear +5"))
        assertEquals(RuleSpec.Linear(lb5), rule("Linear add 5lb"))
        assertEquals(RuleSpec.Linear(Mass(2.5, ScriptWeightUnit.KG)), rule("linear, add 2.5 kg"))
        assertEquals(RuleSpec.Linear(lb5, 10.0, 3), rule("linear, add 5 lb, deload 10% after 3 misses"))
        assertEquals(RuleSpec.Linear(lb5, 10.0, 3), rule("linear, add 5, deload 10% after 3"))
        assertEquals(RuleSpec.Linear(lb5, 10.0, null), rule("linear, add 5, deload 10%"))
        assertEquals(RuleSpec.DoubleProgression(lb5), rule("double progression, add 5 lb"))
        assertEquals(RuleSpec.DoubleProgression(lb5), rule("double, +5"))
        assertEquals(RuleSpec.RpeBased, rule("rpe"))
        assertEquals(RuleSpec.RpeBased, rule("RPE based"))
        assertEquals(RuleSpec.PercentOf1rm(80.0), rule("80% of max"))
        assertEquals(RuleSpec.PercentOf1rm(82.5), rule("82.5% of 1RM"))
        assertEquals(RuleSpec.NoProgression, rule("none"))
        assertEquals(RuleSpec.Custom("weight + 2.5"), rule("custom: weight + 2.5"))
        assertEquals(RuleSpec.Custom("if (reps > 8) weight + 5 else weight"), rule("custom: if (reps > 8) weight + 5 else weight"))
    }

    @Test fun `rules map onto the existing rule types and params`() {
        val linear = RuleSpec.Linear(Mass(5.0, ScriptWeightUnit.LB), 10.0, 3)
        assertEquals(ProgressionType.LINEAR, linear.type)
        val p = linear.toRuleParams()
        assertEquals(2.26796, p.incrementKg, 0.0001)
        assertEquals(10.0, p.deloadPercent, 0.0)
        assertEquals(3, p.failuresBeforeDeload)
        assertEquals(ProgressionType.PERCENTAGE_1RM, RuleSpec.PercentOf1rm(80.0).type)
        assertEquals(80.0, RuleSpec.PercentOf1rm(80.0).toRuleParams().percentageOf1rm, 0.0)
        assertEquals("weight + 1", RuleSpec.Custom("weight + 1").toRuleParams().expression)
        assertNull(RuleSpec.NoProgression.type)
        assertEquals("""{"expression":"if (a) \"x\" else 1"}""", RuleSpec.Custom("if (a) \"x\" else 1").paramsJson())
    }

    @Test fun `the short form is an alias for sets and reps`() {
        val e = exercise("4x6-8\nweight: 100")
        assertEquals(4, e.sets)
        assertEquals(Reps.Range(6, 8), e.reps)
        assertEquals(3, exercise("3 X max").sets)
        assertEquals(Reps.Amrap, exercise("3×AMRAP").reps)
        assertEquals(Reps.Fixed(5), exercise("- 5 x 5").reps)
    }

    @Test fun `notes belong to the program the day or the exercise`() {
        val p = doc(
            """
            # Program: T
            notes: header note
            ## D
            note: day note
            ### X
            sets: 1
            reps: 1
            note: first
            Notes: second
            """,
        )
        assertEquals(listOf("header note"), p.notes)
        assertEquals(listOf("day note"), p.days.single().notes)
        assertEquals(listOf("first", "second"), p.days.single().exercises.single().notes)
    }

    @Test fun `html comments are ignored, including inside a line and across lines`() {
        val p = doc(
            """
            # Program: T <!-- inline -->
            <!-- a comment
            over two lines -->
            ## D
            ### X
            sets: 3 <!-- hidden -->
            reps: 5
            """,
        )
        assertEquals("T", p.name)
        assertEquals(3, p.days.single().exercises.single().sets)
    }

    @Test fun `crlf bullets and trailing spaces are tolerated`() {
        val p = ProgramScript.parse("# Program: T  \r\n\r\n## D  \r\n### X \r\n- sets: 3  \r\n* reps: 5\r\n")
        assertEquals(3, p.document!!.days.single().exercises.single().sets)
    }

    // ---- errors ----

    @Test fun `unknown key is an error with line and column and a suggestion`() {
        val d = firstError("# Program: T\n## D\n### X\nsets: 3\nreps: 5\nweigth: 100")
        assertEquals(DiagnosticCode.UNKNOWN_KEY, d.code)
        assertEquals(6, d.line)
        assertEquals(1, d.column)
        assertTrue(d.message, d.message.contains("Did you mean \"weight\""))
        assertEquals("README#conventions", d.anchor)
    }

    @Test fun `a prose line in an exercise is an error that says what was expected`() {
        val d = error("# Program: T\n## D\n### Bench Press\n4 sets of 6", DiagnosticCode.UNEXPECTED_TEXT)
        assertEquals(4, d.line)
        assertTrue(d.message, d.message.contains("\"4 sets of 6\""))
        assertTrue(d.message, d.message.contains("sets: 4"))
    }

    @Test fun `a missing colon is named`() {
        val d = error("# Program: T\n## D\n### X\nsets 4\nreps: 5", DiagnosticCode.MISSING_COLON)
        assertEquals(4, d.line)
    }

    @Test fun `bad values point at the value`() {
        val d = firstError("# Program: T\n## D\n### X\nsets: 3\nreps: lots")
        assertEquals(DiagnosticCode.BAD_REPS, d.code)
        assertEquals(5, d.line)
        assertEquals(7, d.column)
        assertEquals(DiagnosticCode.BAD_SETS, firstError("# Program: T\n## D\n### X\nsets: many\nreps: 5").code)
        assertEquals(DiagnosticCode.BAD_SETS, firstError("# Program: T\n## D\n### X\nsets: 0\nreps: 5").code)
        assertEquals(DiagnosticCode.BAD_RPE, firstError("# Program: T\n## D\n### X\nsets: 3\nreps: 5\nrpe: 11").code)
        assertEquals(DiagnosticCode.BAD_WEIGHT, firstError("# Program: T\n## D\n### X\nsets: 3\nreps: 5\nweight: heavy").code)
        assertEquals(DiagnosticCode.BAD_REST, firstError("# Program: T\n## D\n### X\nsets: 3\nreps: 5\nrest: 90").code)
        assertEquals(DiagnosticCode.BAD_REPS, firstError("# Program: T\n## D\n### X\nsets: 3\nreps: 8-6").code)
        assertEquals(DiagnosticCode.UNKNOWN_UNITS, firstError("# Program: T\nunits: stone\n## D\n### X\nsets: 3\nreps: 5").code)
    }

    @Test fun `missing required fields and structure are errors`() {
        assertEquals(DiagnosticCode.MISSING_SETS, firstError("# Program: T\n## D\n### X\nreps: 5").code)
        assertEquals(DiagnosticCode.MISSING_REPS, firstError("# Program: T\n## D\n### X\nsets: 5").code)
        assertEquals(DiagnosticCode.NO_DAYS, firstError("# Program: T").code)
        assertEquals(DiagnosticCode.EXERCISE_OUTSIDE_DAY, firstError("# Program: T\n### X\nsets: 3\nreps: 5").code)
        assertEquals(DiagnosticCode.FIELD_OUTSIDE_EXERCISE, firstError("# Program: T\n## D\nsets: 3").code)
        assertEquals(DiagnosticCode.DUPLICATE_KEY, firstError("# Program: T\n## D\n### X\nsets: 3\nsets: 4\nreps: 5").code)
        error("# Program: T\n## D\n### X\nsets:\nreps: 5", DiagnosticCode.EMPTY_VALUE)
        assertEquals(DiagnosticCode.NOT_AN_INTAKE_SCRIPT, firstError("Bench press 3x5").code)
        assertEquals(DiagnosticCode.MISSING_PROGRAM_NAME, firstError("# Program:\n## D\n### X\nsets: 3\nreps: 5").code)
        assertEquals(DiagnosticCode.UNEXPECTED_HEADING, firstError("# Program: T\n## D\n#### X\nsets: 3\nreps: 5").code)
        assertEquals(DiagnosticCode.UNCLOSED_COMMENT, firstError("# Program: T\n<!-- oops\n## D\n### X\nsets: 3\nreps: 5").code)
    }

    @Test fun `rule errors say what was wrong`() {
        fun err(v: String) = firstError("# Program: T\n## D\n### X\nsets: 3\nreps: 5\nrpe: 8\nprogress: $v")
        assertEquals(DiagnosticCode.BAD_RULE, err("linier, add 5").code)
        assertTrue(err("linier, add 5").message.contains("Did you mean \"linear\""))
        assertEquals(DiagnosticCode.RULE_NEEDS_INCREMENT, err("linear").code)
        assertEquals(DiagnosticCode.BAD_PERCENT, err("150% of max").code)
        assertEquals(DiagnosticCode.BAD_DELOAD, err("double, add 5, deload 10%").code)
        assertEquals(DiagnosticCode.BAD_EXPRESSION, err("custom: weight +").code)
        assertEquals(DiagnosticCode.BAD_EXPRESSION, err("custom: bogus + 1").code)
        assertEquals(DiagnosticCode.BAD_EXPRESSION, err("custom:").code)
        val d = err("custom: bogus + 1")
        assertEquals(7, d.line)
        assertTrue(d.message, d.message.contains("bogus"))
    }

    @Test fun `the rpe rule needs an rpe target`() {
        val r = parse("# Program: T\n## D\n### X\nsets: 3\nreps: 5\nprogress: rpe")
        assertEquals(DiagnosticCode.RPE_RULE_NEEDS_RPE, r.errors.single().code)
        val inherited = parse("# Program: T\nprogress: rpe\n## D\n### X\nsets: 3\nreps: 5")
        assertNotNull(inherited.document)
        assertEquals(DiagnosticCode.RPE_RULE_WITHOUT_RPE, inherited.warnings.single().code)
    }

    @Test fun `warnings do not block the import`() {
        val empty = parse("# Program: T\n## A\n### X\nsets: 3\nreps: 5\n## B")
        assertNotNull(empty.document)
        assertEquals(DiagnosticCode.EMPTY_DAY, empty.warnings.single().code)
        val dup = parse("# Program: T\n## A\n### X\nsets: 3\nreps: 5\n## a\n### Y\nsets: 3\nreps: 5")
        assertEquals(DiagnosticCode.DUPLICATE_DAY, dup.warnings.single().code)
        val amrap = parse("# Program: T\nprogress: double, add 5\n## A\n### X\nsets: 3\nreps: max")
        assertEquals(DiagnosticCode.AMRAP_WITH_DOUBLE, amrap.warnings.single().code)
    }

    @Test fun `every error carries a line a column and an anchor`() {
        val r = parse("# Program: T\n## D\n### X\nnonsense here\nsets: x\nreps")
        assertTrue(r.errors.size >= 3)
        r.diagnostics.forEach {
            assertTrue(it.line >= 1)
            assertTrue(it.column >= 1)
            assertTrue(it.anchor.contains('#'))
        }
    }

    // ---- printer ----

    @Test fun `print then parse gives the same program`() {
        val original = doc(
            """
            # Program: Round Trip
            units: lb
            rest: 90s
            progress: linear, add 5 lb, deload 10% after 3 misses
            notes: one
            ## Day One
            note: day
            ### A
            sets: 4
            reps: 6-8
            rpe: 7.5
            weight: bodyweight + 25 kg
            rest: 2:30
            progress: custom: if (rpe > 8) weight else weight + 2.5
            note: hi
            ### B
            sets: 3
            reps: max
            progress: 80% of max
            ## Day Two
            ### C
            sets: 2
            reps: 12
            weight: 62.5
            progress: double progression, add 2.5 kg
            ### D
            sets: 2
            reps: 12
            progress: rpe
            rpe: 9
            ### E
            4x5
            progress: none
            """,
        )
        val printed = ProgramScript.print(original)
        val again = ProgramScript.parse(printed)
        assertTrue(again.diagnostics.toString(), again.errors.isEmpty())
        assertEquals(original, again.document)
        assertEquals(printed, ProgramScript.print(again.document!!))
    }
}
