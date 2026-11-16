package com.zeroindex.intake.ui.components

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * The press ripple of a `clickable` is clipped only by a `clip` that comes before it in the same modifier chain. A
 * rounded or circular `background` followed by a click, with no clip in between, draws a square ripple over round
 * corners (feedback item 18, the Workout / Exercises segmented control). This scan reads each call chain that ends in
 * a click and fails when it has a rounded or circular background and no `clip` before the click.
 *
 * It is narrow on purpose. It reads only chains that carry a `.background(...)` with `RoundedCornerShape` or
 * `CircleShape`. A `Surface(shape = ...)` whose click arrives through its `modifier =` parameter is a different
 * shape of the same bug, and this scan cannot see it; those are fixed by hand.
 */
class ClickableClipOrderTest {

    private val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
    private val clickRx = Regex("""\.(clickable|combinedClickable|selectable|toggleable)\(""")
    private val roundShape = Regex("""RoundedCornerShape|CircleShape""")

    private class Call(val name: String, val args: String)

    @Test
    fun `a rounded background is clipped before its clickable in ui components`() {
        val dir = File(sourceRoot, "com/zeroindex/intake/ui/components")
        val problems = dir.listFiles { f -> f.isFile && f.extension == "kt" }.orEmpty().flatMap { file ->
            val text = file.readText()
            clickRx.findAll(text).mapNotNull { m ->
                val chain = chainBefore(text, m.range.first)
                val roundedBackground = chain.any { it.name == "background" && roundShape.containsMatchIn(it.args) }
                val clipped = chain.any { it.name == "clip" }
                if (roundedBackground && !clipped) {
                    val line = text.substring(0, m.range.first).count { it == '\n' } + 1
                    "${file.name}:$line ${m.value.trim('.', '(')}"
                } else {
                    null
                }
            }.toList()
        }
        assertWithMessage("Clickable after a rounded background with no clip before it: $problems")
            .that(problems)
            .isEmpty()
    }

    /** The `.name(args)` calls that lead up to the dot at [dot], in source order, back to the start of the chain. */
    private fun chainBefore(text: String, dot: Int): List<Call> {
        val calls = ArrayDeque<Call>()
        var j = dot - 1
        while (true) {
            j = skipBackWhitespace(text, j)
            if (j < 0 || text[j] != ')') break
            val open = matchingOpenParen(text, j)
            if (open < 0) break
            var nameStart = open - 1
            while (nameStart >= 0 && (text[nameStart].isLetterOrDigit() || text[nameStart] == '_')) nameStart--
            if (nameStart == open - 1) break
            calls.addFirst(Call(text.substring(nameStart + 1, open), text.substring(open + 1, j)))
            j = skipBackWhitespace(text, nameStart)
            if (j < 0 || text[j] != '.') break
            j--
        }
        return calls.toList()
    }

    private fun skipBackWhitespace(text: String, from: Int): Int {
        var i = from
        while (i >= 0 && text[i].isWhitespace()) i--
        return i
    }

    /** Index of the `(` that matches the `)` at [close], or -1. */
    private fun matchingOpenParen(text: String, close: Int): Int {
        var depth = 0
        for (i in close downTo 0) {
            when (text[i]) {
                ')' -> depth++
                '(' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}
