package com.zeroindex.intake.domain.script

import com.zeroindex.intake.domain.plan.PlanFileParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScriptDocsTest {

    @Test fun `the strength block example parses with zero errors`() {
        val r = ProgramScript.parse(DocsFiles.read("examples/strength-block.md"))
        assertTrue(r.diagnostics.toString(), r.errors.isEmpty())
        assertEquals(3, r.document!!.days.size)
        assertTrue(r.diagnostics.toString(), r.warnings.isEmpty())
    }

    @Test fun `the meal plan sample parses with zero errors`() {
        val r = MealPlanScript.parse(DocsFiles.read("examples/meal-plan-sample.md"))
        assertTrue(r.diagnostics.toString(), r.errors.isEmpty())
        val f = r.document!!.file
        assertEquals(2, f.weeks.size)
        assertEquals(emptyList<String>(), PlanFileParser.validate(f))
        assertEquals(listOf(2), r.document!!.weeksWithGeneratedShopping)
    }

    @Test fun `examples round trip through the printers`() {
        val p = ProgramScript.parse(DocsFiles.read("examples/strength-block.md")).document!!
        assertEquals(p, ProgramScript.parse(ProgramScript.print(p)).document)
        val m = MealPlanScript.parse(DocsFiles.read("examples/meal-plan-sample.md")).document!!
        assertEquals(m, MealPlanScript.parse(MealPlanScript.print(m)).document)
    }

    private fun slug(heading: String): String =
        heading.trim().lowercase().replace(Regex("""[^a-z0-9 \-_]"""), "").replace(' ', '-')

    private fun anchorsOf(page: String): Set<String> =
        DocsFiles.read("$page.md").lines().filter { it.startsWith("#") }
            .map { slug(it.trimStart('#')) }.toSet()

    @Test fun `every diagnostic anchor points at a real wiki heading`() {
        DiagnosticCode.entries.forEach { code ->
            val (page, anchor) = code.anchor.split('#')
            assertTrue("$code anchor $page#$anchor", anchor in anchorsOf(page))
        }
    }

    @Test fun `every diagnostic is documented in errors md`() {
        val anchors = anchorsOf("errors")
        DiagnosticCode.entries.forEach { code ->
            assertTrue("errors.md is missing a heading '${code.slug}'", code.slug in anchors)
        }
    }

    @Test fun `relative links between wiki pages resolve`() {
        val pages = listOf("README", "program", "progress-rules", "meal-plan", "errors", "ai-prompt")
        pages.forEach { page ->
            Regex("""\]\(([^)#\s]*)(?:#([^)\s]*))?\)""").findAll(DocsFiles.read("$page.md")).forEach { m ->
                val target = m.groupValues[1]
                val anchor = m.groupValues[2]
                if (target.startsWith("http") || (target.isEmpty() && anchor.isEmpty())) return@forEach
                val file = target.ifEmpty { "$page.md" }
                assertTrue("$page links to missing $file", File(DocsFiles.dir, file).exists())
                if (anchor.isNotEmpty() && file.endsWith(".md") && !file.contains('/')) {
                    assertTrue("$page links to $file#$anchor", anchor in anchorsOf(file.removeSuffix(".md")))
                }
            }
        }
    }
}
