package com.zeroindex.intake.domain.docs

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.script.DiagnosticCode
import com.zeroindex.intake.domain.script.DocsFiles
import org.junit.Test

class MarkdownTest {

    private val pages = listOf(
        "README", "program", "progress-rules", "meal-plan", "errors", "ai-prompt",
        "examples/strength-block", "examples/meal-plan-sample",
    )

    private fun anchors(page: String) =
        Markdown.parse(DocsFiles.read("$page.md")).filterIsInstance<MdBlock.Heading>().map { it.anchor }.toSet()

    @Test fun `slugs follow the GitHub rule the docs test uses`() {
        assertThat(Markdown.slug("Two-minute quick start")).isEqualTo("two-minute-quick-start")
        assertThat(Markdown.slug("File kinds")).isEqualTo("file-kinds")
        assertThat(Markdown.slug("Percent of max (1RM)!")).isEqualTo("percent-of-max-1rm")
        assertThat(Markdown.slug("  not-an-intake-script ")).isEqualTo("not-an-intake-script")
    }

    @Test fun `every diagnostic anchor is a heading of the rendered page`() {
        DiagnosticCode.entries.forEach { code ->
            val (page, anchor) = code.anchor.split('#')
            assertThat(anchors(page)).contains(anchor)
        }
    }

    @Test fun `every diagnostic has its errors page heading`() {
        val a = anchors("errors")
        DiagnosticCode.entries.forEach { assertThat(a).contains(it.slug) }
    }

    @Test fun `duplicate headings get a numbered suffix`() {
        val h = Markdown.parse("# A\n## Same\n## Same\n").filterIsInstance<MdBlock.Heading>()
        assertThat(h.map { it.anchor }).containsExactly("a", "same", "same-1").inOrder()
    }

    private fun links(inl: List<MdInline>): List<String> = inl.flatMap {
        when (it) {
            is MdInline.Link -> listOf(it.target) + links(it.children)
            is MdInline.Bold -> links(it.children)
            is MdInline.Italic -> links(it.children)
            else -> emptyList()
        }
    }

    @Test fun `all wiki pages parse and links resolve to known pages and anchors`() {
        pages.forEach { page ->
            val blocks = Markdown.parse(DocsFiles.read("$page.md"))
            assertThat(blocks).isNotEmpty()
            val targets = blocks.flatMap {
                when (it) {
                    is MdBlock.Paragraph -> links(it.inlines)
                    is MdBlock.ListBlock -> it.items.flatMap(::links)
                    is MdBlock.Heading -> links(it.inlines)
                    else -> emptyList()
                }
            }
            targets.forEach { t ->
                when (val r = Markdown.resolveLink(t)) {
                    is MdLinkTarget.Page -> {
                        assertThat(pages).contains(r.page)
                        r.anchor?.let { assertThat(anchors(r.page)).contains(it) }
                    }
                    is MdLinkTarget.Anchor -> assertThat(anchors(page)).contains(r.anchor)
                    MdLinkTarget.External -> Unit
                }
            }
        }
    }

    @Test fun `link targets`() {
        assertThat(Markdown.resolveLink("program.md#sets")).isEqualTo(MdLinkTarget.Page("program", "sets"))
        assertThat(Markdown.resolveLink("#limits")).isEqualTo(MdLinkTarget.Anchor("limits"))
        assertThat(Markdown.resolveLink("https://example.com")).isEqualTo(MdLinkTarget.External)
        assertThat(Markdown.resolveLink("mailto:a@b.c")).isEqualTo(MdLinkTarget.External)
    }

    @Test fun `blocks`() {
        val md = listOf(
            "# Title",
            "",
            "Some **bold**, *italic* and `code` with a [link](a.md#x).",
            "Second line.",
            "",
            "- one",
            "  wrapped",
            "* two",
            "",
            "1. first",
            "2. second",
            "",
            "| A | B |",
            "|---|---|",
            "| 1 | 2 |",
            "",
            "````text",
            "```",
            "inside",
            "```",
            "````",
        ).joinToString("\n")
        val b = Markdown.parse(md)
        assertThat(b[0]).isInstanceOf(MdBlock.Heading::class.java)
        val p = b[1] as MdBlock.Paragraph
        assertThat(Markdown.plainText(p.inlines)).isEqualTo("Some bold, italic and code with a link. Second line.")
        assertThat(p.inlines.filterIsInstance<MdInline.Bold>()).hasSize(1)
        assertThat(p.inlines.filterIsInstance<MdInline.Italic>()).hasSize(1)
        assertThat(p.inlines.filterIsInstance<MdInline.Code>().single().text).isEqualTo("code")
        val bullets = b[2] as MdBlock.ListBlock
        assertThat(bullets.ordered).isFalse()
        assertThat(bullets.items.map(Markdown::plainText)).containsExactly("one wrapped", "two").inOrder()
        assertThat((b[3] as MdBlock.ListBlock).ordered).isTrue()
        assertThat((b[4] as MdBlock.Table).rows).containsExactly(listOf("A", "B"), listOf("1", "2")).inOrder()
        val code = b[5] as MdBlock.Code
        assertThat(code.language).isEqualTo("text")
        assertThat(code.text).isEqualTo("```\ninside\n```")
    }

    @Test fun `the ai prompt page has one copyable block per kind`() {
        val prompts = Markdown.fencedBlocks(DocsFiles.read("ai-prompt.md"), "text")
        assertThat(prompts).hasSize(2)
        assertThat(prompts[0]).contains("# Program:")
        assertThat(prompts[1]).contains("# Meal plan:")
    }
}
