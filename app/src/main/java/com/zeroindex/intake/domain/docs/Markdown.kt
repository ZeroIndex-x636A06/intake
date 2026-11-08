package com.zeroindex.intake.domain.docs

/** Inline pieces of a line of Markdown. */
sealed interface MdInline {
    data class Text(val text: String) : MdInline
    data class Bold(val children: List<MdInline>) : MdInline
    data class Italic(val children: List<MdInline>) : MdInline
    data class Code(val text: String) : MdInline
    data class Link(val children: List<MdInline>, val target: String) : MdInline
}

sealed interface MdBlock {
    /** [anchor] is the GitHub-style slug, made unique within the page. */
    data class Heading(val level: Int, val inlines: List<MdInline>, val anchor: String) : MdBlock
    data class Paragraph(val inlines: List<MdInline>) : MdBlock
    data class ListBlock(val ordered: Boolean, val items: List<List<MdInline>>) : MdBlock
    data class Code(val language: String, val text: String) : MdBlock
    /** [rows] includes the header row first; the separator row is dropped. */
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

/** Where a link goes. External links are never followed: the docs make no network calls. */
sealed interface MdLinkTarget {
    data class Page(val page: String, val anchor: String?) : MdLinkTarget
    data class Anchor(val anchor: String) : MdLinkTarget
    data object External : MdLinkTarget
}

/**
 * A small Markdown reader for the wiki in docs/scripting: headings, paragraphs, bold, italics, inline
 * code, bullet and numbered lists, fenced code, tables, rules and links. Pure, so it is unit tested
 * against the real pages. Anything it does not know is shown as plain text.
 */
object Markdown {

    /** GitHub-style heading anchor. Matches the slugs ScriptDocsTest checks the diagnostics against. */
    fun slug(heading: String): String =
        heading.trim().lowercase().replace(Regex("""[^a-z0-9 \-_]"""), "").replace(' ', '-')

    fun resolveLink(target: String): MdLinkTarget {
        val t = target.trim()
        if (Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:""").containsMatchIn(t)) return MdLinkTarget.External
        val hash = t.indexOf('#')
        val path = if (hash >= 0) t.substring(0, hash) else t
        val anchor = if (hash >= 0) t.substring(hash + 1).ifEmpty { null } else null
        if (path.isEmpty()) return if (anchor != null) MdLinkTarget.Anchor(anchor) else MdLinkTarget.External
        return MdLinkTarget.Page(path.removePrefix("./").removeSuffix(".md"), anchor)
    }

    fun parse(text: String): List<MdBlock> {
        val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<MdBlock>()
        val usedSlugs = HashMap<String, Int>()
        var i = 0

        fun isBlank(s: String) = s.isBlank()
        fun fence(s: String): Pair<Int, String>? {
            val m = Regex("""^ {0,3}(`{3,})\s*([A-Za-z0-9_+-]*)\s*$""").find(s) ?: return null
            return m.groupValues[1].length to m.groupValues[2]
        }
        val bullet = Regex("""^\s{0,3}[-*+]\s+(.*)$""")
        val numbered = Regex("""^\s{0,3}\d+[.)]\s+(.*)$""")
        fun startsBlock(s: String) =
            fence(s) != null || s.trimStart().startsWith("#") && Regex("""^ {0,3}#{1,6}\s""").containsMatchIn(s) ||
                s.trimStart().startsWith("|") || bullet.containsMatchIn(s) || numbered.containsMatchIn(s) ||
                Regex("""^ {0,3}(-{3,}|\*{3,}|_{3,})\s*$""").containsMatchIn(s)

        while (i < lines.size) {
            val line = lines[i]
            if (isBlank(line)) { i++; continue }

            val f = fence(line)
            if (f != null) {
                val buf = StringBuilder()
                i++
                while (i < lines.size) {
                    val close = Regex("""^ {0,3}(`{${f.first},})\s*$""").containsMatchIn(lines[i])
                    if (close) break
                    buf.append(lines[i]).append('\n')
                    i++
                }
                i++ // closing fence (or end of file)
                out += MdBlock.Code(f.second, buf.toString().trimEnd('\n'))
                continue
            }

            val h = Regex("""^ {0,3}(#{1,6})\s+(.*?)\s*#*\s*$""").find(line)
            if (h != null) {
                val level = h.groupValues[1].length
                val raw = h.groupValues[2]
                val base = slug(raw)
                val n = usedSlugs[base] ?: 0
                usedSlugs[base] = n + 1
                out += MdBlock.Heading(level, inlines(raw), if (n == 0) base else "$base-$n")
                i++
                continue
            }

            if (Regex("""^ {0,3}(-{3,}|\*{3,}|_{3,})\s*$""").containsMatchIn(line)) {
                out += MdBlock.Rule; i++; continue
            }

            if (line.trimStart().startsWith("|")) {
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    val cells = splitRow(lines[i])
                    val isSeparator = cells.isNotEmpty() && cells.all { Regex("""^:?-{1,}:?$""").matches(it) }
                    if (!isSeparator) rows += cells
                    i++
                }
                out += MdBlock.Table(rows)
                continue
            }

            val isBullet = bullet.containsMatchIn(line)
            val isNumbered = !isBullet && numbered.containsMatchIn(line)
            if (isBullet || isNumbered) {
                val pattern = if (isBullet) bullet else numbered
                val items = ArrayList<List<MdInline>>()
                while (i < lines.size) {
                    val m = pattern.find(lines[i]) ?: break
                    val item = StringBuilder(m.groupValues[1])
                    i++
                    // Continuation: indented lines that are not a new list item or block.
                    while (i < lines.size && lines[i].isNotBlank() && lines[i].startsWith(" ") &&
                        !bullet.containsMatchIn(lines[i]) && !numbered.containsMatchIn(lines[i])
                    ) {
                        item.append(' ').append(lines[i].trim())
                        i++
                    }
                    items += inlines(item.toString())
                }
                out += MdBlock.ListBlock(ordered = isNumbered, items = items)
                continue
            }

            // Paragraph: consecutive lines until a blank line or another block starts.
            val buf = StringBuilder(line.trim())
            i++
            while (i < lines.size && lines[i].isNotBlank() && !startsBlock(lines[i])) {
                buf.append(' ').append(lines[i].trim())
                i++
            }
            out += MdBlock.Paragraph(inlines(buf.toString()))
        }
        return out
    }

    /** The text of every fenced block with the given language, in order (used by "Copy prompt"). */
    fun fencedBlocks(text: String, language: String): List<String> =
        parse(text).filterIsInstance<MdBlock.Code>().filter { it.language == language }.map { it.text }

    private fun splitRow(line: String): List<String> {
        val t = line.trim().removePrefix("|").removeSuffix("|")
        val cells = ArrayList<String>()
        val cur = StringBuilder()
        var k = 0
        while (k < t.length) {
            val c = t[k]
            if (c == '\\' && k + 1 < t.length && t[k + 1] == '|') { cur.append('|'); k += 2; continue }
            if (c == '|') { cells += cur.toString().trim(); cur.clear() } else cur.append(c)
            k++
        }
        cells += cur.toString().trim()
        return cells
    }

    // ---- inline ---------------------------------------------------------------------------------

    fun inlines(s: String): List<MdInline> {
        val out = ArrayList<MdInline>()
        val plain = StringBuilder()
        fun flush() { if (plain.isNotEmpty()) { out += MdInline.Text(plain.toString()); plain.clear() } }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_{}[]()#+-.!|<>" -> { plain.append(s[i + 1]); i += 2 }
                c == '`' -> {
                    var ticks = 0
                    while (i + ticks < s.length && s[i + ticks] == '`') ticks++
                    val marker = "`".repeat(ticks)
                    val end = s.indexOf(marker, i + ticks)
                    if (end < 0) { plain.append(marker); i += ticks } else {
                        flush()
                        out += MdInline.Code(s.substring(i + ticks, end).trim())
                        i = end + ticks
                    }
                }
                c == '[' -> {
                    val close = findClosingBracket(s, i)
                    if (close > 0 && close + 1 < s.length && s[close + 1] == '(') {
                        val end = s.indexOf(')', close + 2)
                        if (end > 0) {
                            flush()
                            out += MdInline.Link(inlines(s.substring(i + 1, close)), s.substring(close + 2, end).trim())
                            i = end + 1
                            continue
                        }
                    }
                    plain.append(c); i++
                }
                s.startsWith("**", i) || s.startsWith("__", i) -> {
                    val mark = s.substring(i, i + 2)
                    val end = s.indexOf(mark, i + 2)
                    if (end > i + 2) {
                        flush()
                        out += MdInline.Bold(inlines(s.substring(i + 2, end)))
                        i = end + 2
                    } else { plain.append(mark); i += 2 }
                }
                c == '*' || (c == '_' && (i == 0 || !s[i - 1].isLetterOrDigit())) -> {
                    val end = findItalicEnd(s, i + 1, c)
                    if (end > i + 1 && !s[i + 1].isWhitespace()) {
                        flush()
                        out += MdInline.Italic(inlines(s.substring(i + 1, end)))
                        i = end + 1
                    } else { plain.append(c); i++ }
                }
                else -> { plain.append(c); i++ }
            }
        }
        flush()
        return out
    }

    private fun findClosingBracket(s: String, open: Int): Int {
        var depth = 0
        var k = open
        while (k < s.length) {
            when (s[k]) {
                '\\' -> k++
                '`' -> { val e = s.indexOf('`', k + 1); if (e > 0) k = e }
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return k }
            }
            k++
        }
        return -1
    }

    private fun findItalicEnd(s: String, from: Int, mark: Char): Int {
        var k = from
        while (k < s.length) {
            val ch = s[k]
            if (ch == '`') { val e = s.indexOf('`', k + 1); if (e > 0) { k = e + 1; continue } }
            if (ch == mark && !s[k - 1].isWhitespace() && (mark == '*' || k + 1 >= s.length || !s[k + 1].isLetterOrDigit())) {
                if (mark == '*' && k + 1 < s.length && s[k + 1] == '*') { k += 2; continue }
                return k
            }
            k++
        }
        return -1
    }

    /** Plain text of inline spans, for TalkBack labels and tests. */
    fun plainText(inlines: List<MdInline>): String = inlines.joinToString("") {
        when (it) {
            is MdInline.Text -> it.text
            is MdInline.Bold -> plainText(it.children)
            is MdInline.Italic -> plainText(it.children)
            is MdInline.Code -> it.text
            is MdInline.Link -> plainText(it.children)
        }
    }
}
