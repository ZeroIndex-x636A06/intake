package com.zeroindex.intake.domain.pdf

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/** Why a PDF could not be split into pages here. The message is fit to show the person. */
class PdfUnsupportedException(message: String) : Exception(message)

/**
 * Reads the page structure of a PDF and writes any run of pages back out as a smaller, standalone PDF, so a
 * large document can be sent to a model in bounded pieces (B4) instead of as one huge request. Pure Kotlin on
 * bytes: no Android classes, and no PDF library (none is in the build, and adding one needs a dependency
 * check).
 *
 * How it reads: it does not trust the cross-reference table. It scans the file for `N G obj ... endobj`,
 * unpacks object streams (Flate), finds the last `/Type /Catalog`, and walks the `/Pages` tree for the page
 * order, carrying down inherited `/Resources`, `/MediaBox`, `/CropBox` and `/Rotate`. Extracting pages
 * copies the objects reachable from them (fonts, images, content), drops links and annotations, and writes a
 * fresh classic cross-reference table. The page content itself is never decoded or changed.
 *
 * What it refuses ([PdfUnsupportedException]): encrypted files, and files whose object streams use a
 * predictor or a filter other than Flate. Those are reported plainly rather than half-handled.
 */
class PdfDocument private constructor(
    private val objects: Map<Int, PdfObject>,
    private val pages: List<PdfPage>,
    private val maxObjectNumber: Int,
) {
    val pageCount: Int get() = pages.size

    /**
     * Roughly how many bytes page [index] adds to an extract: the sum of everything reachable from it. Shared
     * fonts count on every page that uses them, so this overestimates a run of pages, which is the safe side
     * for keeping requests small.
     */
    fun estimatedBytes(index: Int): Long = reachable(listOf(pages[index])).sumOf { objects.getValue(it).raw.length.toLong() }

    /** Pages [from]..[to] (zero-based, inclusive) as their own PDF. */
    fun extract(from: Int, to: Int): ByteArray {
        require(from in pages.indices && to in from until pages.size) { "pages $from..$to are outside 0..${pages.size - 1}" }
        val chosen = pages.subList(from, to + 1)
        val pagesNum = maxObjectNumber + 1
        val catalogNum = maxObjectNumber + 2
        val included = reachable(chosen)

        val out = ByteArrayOutputStream()
        val offsets = HashMap<Int, Int>()

        fun write(text: String) = out.write(text.toByteArray(Charsets.ISO_8859_1))

        write("%PDF-1.7\n%âãÏÓ\n")
        for (num in included.sorted()) {
            offsets[num] = out.size()
            val obj = objects.getValue(num)
            val page = chosen.firstOrNull { it.number == num }
            write(if (page != null) rewritePage(obj, page, pagesNum) else obj.raw)
            write("\n")
        }
        offsets[pagesNum] = out.size()
        write(
            "$pagesNum 0 obj\n<< /Type /Pages /Kids [${chosen.joinToString(" ") { "${it.number} 0 R" }}] " +
                "/Count ${chosen.size} >>\nendobj\n",
        )
        offsets[catalogNum] = out.size()
        write("$catalogNum 0 obj\n<< /Type /Catalog /Pages $pagesNum 0 R >>\nendobj\n")

        val xrefAt = out.size()
        val size = catalogNum + 1
        write("xref\n0 $size\n")
        write("0000000000 65535 f \n")
        for (n in 1 until size) {
            val off = offsets[n]
            write(if (off == null) "0000000000 00000 f \n" else "${off.toString().padStart(10, '0')} 00000 n \n")
        }
        write("trailer\n<< /Size $size /Root $catalogNum 0 R >>\nstartxref\n$xrefAt\n%%EOF\n")
        return out.toByteArray()
    }

    /** Object numbers reachable from [roots], never climbing to /Parent, so one page cannot pull in the whole tree. */
    private fun reachable(roots: List<PdfPage>): Set<Int> {
        val seen = LinkedHashSet<Int>()
        val stack = ArrayDeque<Int>()
        roots.forEach { stack.addLast(it.number) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (!seen.add(n)) continue
            val obj = objects[n] ?: continue
            val root = roots.firstOrNull { it.number == n }
            // A page's inherited resources are copied onto it, so what they reference travels with it.
            val dict = if (root != null) {
                stripPageKeys(obj.dict).replace(PARENT, "") + " " + root.inherited.values.joinToString(" ")
            } else {
                obj.dict
            }
            for (m in REF.findAll(dict)) {
                val target = m.groupValues[1].toInt()
                if (target in objects && target !in seen) stack.addLast(target)
            }
        }
        // Objects that only exist as dangling references are skipped; the page objects themselves are kept.
        return seen.filterTo(LinkedHashSet()) { it in objects }
    }

    private fun rewritePage(obj: PdfObject, page: PdfPage, newParent: Int): String {
        var dict = stripPageKeys(obj.dict).trim()
        dict = dict.replace(PARENT, "").trim()
        require(dict.startsWith("<<") && dict.endsWith(">>")) { "page ${page.number} is not a dictionary" }
        val extras = StringBuilder()
        for ((key, value) in page.inherited) if (!hasKey(dict, key)) extras.append(" /$key $value")
        val body = dict.removePrefix("<<").removeSuffix(">>")
        return "${page.number} 0 obj\n<<$body /Parent $newParent 0 R$extras >>\nendobj"
    }

    private fun stripPageKeys(dict: String): String {
        var d = dict
        for (key in DROPPED_PAGE_KEYS) d = removeKey(d, key)
        return d
    }

    internal class PdfObject(val number: Int, val dict: String, val raw: String)

    internal class PdfPage(val number: Int, val inherited: Map<String, String>)

    companion object {
        /** Links, article threads, actions and thumbnails point at other pages; none matters for reading a table. */
        private val DROPPED_PAGE_KEYS = listOf("Annots", "B", "AA", "Thumb")
        private val INHERITABLE = listOf("Resources", "MediaBox", "CropBox", "Rotate")
        private val HEADER = Regex("""(?<![0-9])(\d{1,10})\s+(\d{1,5})\s+obj(?![a-z])""")
        private val REF = Regex("""(?<![0-9])(\d{1,10})\s+\d{1,5}\s+R(?![a-zA-Z0-9])""")
        private val PARENT = Regex("""/Parent\s+\d+\s+\d+\s+R""")
        private val STREAM_KEYWORD = Regex(""">>\s*stream\r?\n""")

        /** Parses [bytes], or throws [PdfUnsupportedException] with a message to show the person. */
        fun parse(bytes: ByteArray): PdfDocument {
            if (bytes.size < 8 || String(bytes, 0, 5, Charsets.ISO_8859_1) != "%PDF-") {
                throw PdfUnsupportedException("That file is not a PDF.")
            }
            val text = String(bytes, Charsets.ISO_8859_1)
            val objects = scanObjects(text)
            if (text.contains("/Encrypt")) throw PdfUnsupportedException("This PDF is password protected, so it cannot be read.")

            // Object streams carry the page tree in many modern files.
            val unpacked = HashMap<Int, PdfObject>()
            for (obj in objects.values) {
                if (OBJSTM.containsMatchIn(obj.dict) && obj.raw.contains("stream")) unpackObjectStream(obj, unpacked)
            }
            val all = HashMap<Int, PdfObject>(objects)
            for ((n, o) in unpacked) all.putIfAbsent(n, o)

            val catalog = all.values.filter { CATALOG.containsMatchIn(it.dict) }.maxByOrNull { it.number }
                ?: throw PdfUnsupportedException("This PDF's page list could not be found.")
            val rootPages = valueOf(catalog.dict, "Pages")?.let { REF.find(it)?.groupValues?.get(1)?.toInt() }
                ?: throw PdfUnsupportedException("This PDF's page list could not be found.")

            val pages = ArrayList<PdfPage>()
            fun walk(num: Int, inherited: Map<String, String>, depth: Int) {
                if (depth > 40) throw PdfUnsupportedException("This PDF's page tree is too deep to read.")
                val node = all[num] ?: return
                val own = INHERITABLE.mapNotNull { k -> valueOf(node.dict, k)?.let { k to it } }.toMap()
                val merged = inherited + own
                val kids = valueOf(node.dict, "Kids")
                if (kids != null) {
                    REF.findAll(kids).forEach { walk(it.groupValues[1].toInt(), merged, depth + 1) }
                } else {
                    // Only what the page lacks is inherited, so it is filled in at extract time.
                    pages += PdfPage(num, merged.filterKeys { it !in own })
                }
            }
            walk(rootPages, emptyMap(), 0)
            if (pages.isEmpty()) throw PdfUnsupportedException("This PDF has no pages.")
            return PdfDocument(all, pages, all.keys.max())
        }

        private val OBJSTM = Regex("""/Type\s*/ObjStm""")
        private val CATALOG = Regex("""/Type\s*/Catalog(?![a-zA-Z])""")

        /** Every top-level `N G obj ... endobj`, stepping over stream bodies so binary data is never mistaken for objects. */
        private fun scanObjects(text: String): Map<Int, PdfObject> {
            val found = HashMap<Int, PdfObject>()
            var at = 0
            while (true) {
                val m = HEADER.find(text, at) ?: break
                val num = m.groupValues[1].toInt()
                val bodyStart = m.range.last + 1
                val firstEnd = text.indexOf("endobj", bodyStart)
                if (firstEnd < 0) break
                var dict: String
                var end: Int
                // Looked for only inside this object's own text, so a long file is scanned once, not once per object.
                val streamKw = STREAM_KEYWORD.find(text.substring(bodyStart, firstEnd))
                if (streamKw != null) {
                    dict = text.substring(bodyStart, bodyStart + streamKw.range.first + 2)
                    val dataStart = bodyStart + streamKw.range.last + 1
                    val length = LENGTH.find(dict)?.groupValues?.get(1)?.toIntOrNull()
                    val dataEnd = if (length != null && text.startsWith("endstream", skipWhitespace(text, dataStart + length))) {
                        dataStart + length
                    } else {
                        text.indexOf("endstream", dataStart).takeIf { it >= 0 } ?: break
                    }
                    end = text.indexOf("endobj", dataEnd).takeIf { it >= 0 } ?: break
                } else {
                    dict = text.substring(bodyStart, firstEnd)
                    end = firstEnd
                }
                // The last definition of an object wins, as in an incrementally updated file.
                found[num] = PdfObject(num, dict.trim(), text.substring(m.range.first, end + "endobj".length))
                at = end + "endobj".length
            }
            return found
        }

        private val LENGTH = Regex("""/Length\s+(\d+)(?!\s+\d+\s+R)""")

        private fun skipWhitespace(text: String, from: Int): Int {
            var i = from
            while (i < text.length && text[i].isWhitespace()) i++
            return i
        }

        /** Adds the objects held inside an object stream to [into]. They never carry streams of their own. */
        private fun unpackObjectStream(stream: PdfObject, into: MutableMap<Int, PdfObject>) {
            val count = valueOf(stream.dict, "N")?.trim()?.toIntOrNull() ?: return
            val first = valueOf(stream.dict, "First")?.trim()?.toIntOrNull() ?: return
            val filter = valueOf(stream.dict, "Filter")?.trim()
            if (filter != null && filter != "/FlateDecode") {
                throw PdfUnsupportedException("This PDF uses a compression this reader does not support.")
            }
            if (stream.dict.contains("/Predictor")) {
                throw PdfUnsupportedException("This PDF uses a compression this reader does not support.")
            }
            val kw = STREAM_KEYWORD.find(stream.raw) ?: return
            val dataStart = kw.range.last + 1
            val dataEnd = stream.raw.lastIndexOf("endstream").takeIf { it > dataStart } ?: return
            var data = stream.raw.substring(dataStart, dataEnd).toByteArray(Charsets.ISO_8859_1)
            if (filter == "/FlateDecode") data = inflate(data) ?: return
            val body = String(data, Charsets.ISO_8859_1)
            val header = body.substring(0, minOf(first, body.length)).trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
            val entries = (0 until count).mapNotNull { i ->
                val num = header.getOrNull(2 * i) ?: return@mapNotNull null
                val off = header.getOrNull(2 * i + 1) ?: return@mapNotNull null
                num to off
            }
            entries.forEachIndexed { i, (num, off) ->
                val start = first + off
                val end = if (i + 1 < entries.size) first + entries[i + 1].second else body.length
                if (start in 0..body.length && end in start..body.length) {
                    val objText = body.substring(start, end).trim()
                    into[num] = PdfObject(num, objText, "$num 0 obj\n$objText\nendobj")
                }
            }
        }

        private fun inflate(data: ByteArray): ByteArray? {
            val inflater = Inflater()
            return try {
                inflater.setInput(data)
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } catch (_: Exception) {
                null
            } finally {
                inflater.end()
            }
        }

        private fun hasKey(dict: String, key: String): Boolean = Regex("""/$key(?![A-Za-z0-9])""").containsMatchIn(dict)

        /**
         * The value written after `/Key` in a dictionary: a balanced `<<...>>` or `[...]`, a reference
         * (`12 0 R`), or a single token. Null when the key is absent. Strings in the value are skipped over
         * so a bracket inside one cannot unbalance it.
         */
        internal fun valueOf(dict: String, key: String): String? {
            val m = Regex("""/$key(?![A-Za-z0-9])""").find(dict) ?: return null
            return valueAt(dict, skipWhitespace(dict, m.range.last + 1))?.first
        }

        /** The value starting at [start] and the index just after it. */
        private fun valueAt(s: String, start: Int): Pair<String, Int>? {
            if (start >= s.length) return null
            return when {
                s.startsWith("<<", start) -> balanced(s, start, "<<", ">>")
                s[start] == '[' -> balanced(s, start, "[", "]")
                s[start] == '(' -> string(s, start)
                s[start] == '/' -> {
                    var i = start + 1
                    while (i < s.length && !s[i].isWhitespace() && s[i] !in "/<>[]()") i++
                    s.substring(start, i) to i
                }
                else -> {
                    val ref = Regex("""\G(\d+)\s+(\d+)\s+R(?![a-zA-Z0-9])""").find(s, start)
                    if (ref != null) {
                        ref.value to ref.range.last + 1
                    } else {
                        var i = start
                        while (i < s.length && !s[i].isWhitespace() && s[i] !in "/<>[]()") i++
                        if (i == start) null else s.substring(start, i) to i
                    }
                }
            }
        }

        private fun string(s: String, start: Int): Pair<String, Int> {
            var depth = 0
            var i = start
            while (i < s.length) {
                when (s[i]) {
                    '\\' -> i++
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return s.substring(start, i + 1) to i + 1
                    }
                }
                i++
            }
            return s.substring(start) to s.length
        }

        private fun balanced(s: String, start: Int, open: String, close: String): Pair<String, Int>? {
            var depth = 0
            var i = start
            while (i < s.length) {
                when {
                    s[i] == '(' -> i = string(s, i).second - 1
                    s.startsWith(open, i) -> {
                        depth++
                        i += open.length - 1
                    }
                    s.startsWith(close, i) -> {
                        depth--
                        i += close.length - 1
                        if (depth == 0) return s.substring(start, i + 1) to i + 1
                    }
                }
                i++
            }
            return null
        }

        /** [dict] without `/Key value`, or unchanged when the key is absent. */
        internal fun removeKey(dict: String, key: String): String {
            val m = Regex("""/$key(?![A-Za-z0-9])""").find(dict) ?: return dict
            val valueStart = skipWhitespace(dict, m.range.last + 1)
            val (_, after) = valueAt(dict, valueStart) ?: return dict
            return dict.substring(0, m.range.first) + dict.substring(after)
        }
    }
}
