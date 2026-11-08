package com.zeroindex.intake.domain.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

internal object DocsFiles {
    val dir: File = listOf("../docs/scripting", "docs/scripting").map { File(it) }.first { it.isDirectory }
    fun read(path: String): String = File(dir, path).readText()
}

class ScriptRobustnessTest {

    private val program = DocsFiles.read("examples/strength-block.md")
    private val plan = DocsFiles.read("examples/meal-plan-sample.md")

    private fun check(text: String): ScriptResult<ScriptDocument> {
        val r = IntakeScript.parse(text)
        if (r.document != null) assertTrue(r.errors.isEmpty())
        r.diagnostics.forEach {
            assertTrue(it.line >= 1)
            assertTrue(it.column >= 1)
        }
        if (r.document != null) {
            // Whatever parsed must print and parse back to itself.
            val printed = IntakeScript.print(r.document)
            val again = IntakeScript.parse(printed)
            assertTrue("round trip failed:\n$printed\n${again.diagnostics}", again.errors.isEmpty())
            assertEquals(r.document, again.document)
        }
        return r
    }

    @Test fun `random garbage never throws`() {
        val rnd = Random(42)
        val alphabet = "abcXYZ019 \t\n\r:-*+#<!>=%@,.;()[]/×–— \u0000éß"
        repeat(3000) {
            val len = rnd.nextInt(0, 400)
            val s = buildString { repeat(len) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            check(s)
            check("# Program: x\n$s")
            check("# Meal plan: x\n$s")
        }
    }

    @Test fun `random lines built from script fragments never throw`() {
        val rnd = Random(7)
        val frags = listOf(
            "# Program: A", "# Meal plan: B", "## Day", "## Week 1: Dish", "## Week 99: x", "## Every week", "## Eat out",
            "### Bench", "### Recipe: x", "### Subway", "sets: 3", "reps: 6-8", "reps: max", "weight: 185 lb",
            "rest: 2:30", "progress: linear, add 5", "progress: custom: weight +", "rpe: 8", "note: hi",
            "Protein: beef", "Servings: 4", "Time: 45 min", "Ingredients:", "Steps:", "Shopping:", "Pantry:",
            "- 3 lb beef", "- Produce: apples, pears", "1. Brown it, 10 min.", "  weight: 370 g",
            "  per 100g: 1 kcal", "  caution: x", "  weeks: 1, 3-5", "  estimate: 1 kcal", "<!--", "-->", "", "4x6", "units: lb",
            "avoid: dairy", "About:", "Notes:", "Serve:", "Variations:", "- ", "-", "###", "#",
        )
        repeat(4000) {
            val n = rnd.nextInt(1, 25)
            val text = (1..n).joinToString(if (rnd.nextBoolean()) "\n" else "\r\n") { frags[rnd.nextInt(frags.size)] }
            check(text)
        }
    }

    @Test fun `every truncation of the examples returns diagnostics or a document, never an exception`() {
        listOf(program, plan).forEach { full ->
            for (cut in 0..full.length step 7) check(full.substring(0, cut))
            check(full)
        }
    }

    @Test fun `deleting or duplicating any line of the examples never throws`() {
        val rnd = Random(3)
        listOf(program, plan).forEach { full ->
            val lines = full.lines()
            for (i in lines.indices) {
                check(lines.filterIndexed { j, _ -> j != i }.joinToString("\n"))
                check((lines.take(i + 1) + lines.drop(i)).joinToString("\n"))
            }
            repeat(300) {
                val chars = full.toCharArray()
                repeat(rnd.nextInt(1, 6)) { chars[rnd.nextInt(chars.size)] = " :-#x1\n,%<>"[rnd.nextInt(11)] }
                check(String(chars))
            }
        }
    }

    @Test fun `empty and huge inputs`() {
        assertNull(check("").document)
        assertNull(check("\n\n   \n").document)
        assertNull(check("x".repeat(3_000_000)).document)
        assertNull(check("# Program: T\n" + "## D\n".repeat(20000)).document)
        assertTrue(check("# Program: T\n" + "nonsense\n".repeat(5000)).diagnostics.size <= 101)
    }

    @Test fun `deeply nested custom expression is an error not a crash`() {
        val expr = "(".repeat(5000) + "1" + ")".repeat(5000)
        val r = ProgramScript.parse("# Program: T\n## D\n### X\nsets: 3\nreps: 5\nprogress: custom: $expr")
        assertNull(r.document)
    }

    @Test fun `detect kinds`() {
        assertEquals(ScriptKind.PROGRAM, IntakeScript.detect("\n# program: x"))
        assertEquals(ScriptKind.MEAL_PLAN, IntakeScript.detect("# Meal  Plan: x"))
        assertNull(IntakeScript.detect("# Notes"))
    }
}
