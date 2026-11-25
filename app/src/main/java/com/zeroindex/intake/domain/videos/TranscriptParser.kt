package com.zeroindex.intake.domain.videos

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Which caption language to ask yt-dlp for and which of the files it wrote to read: English first, then the device language. */
object TranscriptLanguage {
    /** Language codes for `--sub-langs`: English, then the device language when it is different. */
    fun requested(deviceLanguage: String): List<String> =
        listOf("en", base(deviceLanguage)).filter { it.isNotBlank() }.distinct()

    /** The best of [available] (codes like `en`, `en-US`, `en-orig`, `de`), or null when neither language is there. */
    fun pick(available: List<String>, deviceLanguage: String): String? {
        for (want in requested(deviceLanguage)) {
            available.firstOrNull { it.equals(want, ignoreCase = true) }?.let { return it }
            available.filter { base(it) == want }.minWithOrNull(compareBy({ it.length }, { it }))?.let { return it }
        }
        return null
    }

    private fun base(code: String): String = code.lowercase().substringBefore('-').substringBefore('_').trim()
}

/**
 * Caption files to timed segments: WebVTT and YouTube's json3. Deterministic, on the phone. Both clean the text the same
 * way ([clean]) and remove the repeats that automatic captions have: a rolling caption shows the previous line again as
 * its first line, so only the new words of each cue are kept.
 */
object TranscriptParser {
    private val VTT_TIME = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})\s*-->\s*(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")
    private val TAG = Regex("""<[^>]*>""")
    private val BRACKET_CUE = Regex(
        """[\[(]\s*(?:music|applause|laughter|laughing|cheering|silence|noise|intro music|upbeat music|background music|inaudible|crowd|singing|clapping)[^\])]*[\])]""",
        RegexOption.IGNORE_CASE,
    )
    private val NOTES = Regex("""[♪♫♬🎵🎶]+""")
    private val SPEAKER = Regex("""^>>\s*""")
    private val SPACES = Regex("""\s+""")
    private val HEX_ENTITY = Regex("""&#x([0-9a-fA-F]+);""")
    private val DEC_ENTITY = Regex("""&#(\d+);""")

    private class Cue(val start: Double, val end: Double, val lines: List<String>)

    /** True for a segment that is only a sound cue: "[Music]", "♪♪", "(applause)". */
    fun isCue(text: String): Boolean = clean(text).isEmpty()

    fun clean(raw: String): String {
        var t = decodeEntities(TAG.replace(raw, ""))
        t = BRACKET_CUE.replace(t, " ")
        t = NOTES.replace(t, " ")
        t = SPEAKER.replace(t.trim(), "")
        return SPACES.replace(t, " ").trim()
    }

    fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        var t = HEX_ENTITY.replace(s) { m -> codePoint(m.groupValues[1].toIntOrNull(16)) }
        t = DEC_ENTITY.replace(t) { m -> codePoint(m.groupValues[1].toIntOrNull()) }
        // &amp; last, so "&amp;lt;" is not decoded twice.
        return t.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&nbsp;", " ").replace("&amp;", "&")
    }

    private fun codePoint(cp: Int?): String = if (cp != null && cp in 1..0x10FFFF) String(Character.toChars(cp)) else ""

    /** WebVTT (a `.vtt` file, manual or automatic captions). */
    fun parseVtt(text: String): List<TranscriptSegment> {
        val cues = mutableListOf<Cue>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        var i = 0
        while (i < lines.size) {
            val m = VTT_TIME.find(lines[i])
            if (m == null) {
                i++
                continue
            }
            val v = m.groupValues
            val start = time(v[1], v[2], v[3], v[4])
            val end = time(v[5], v[6], v[7], v[8])
            i++
            val body = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                body += lines[i]
                i++
            }
            cues += Cue(start, end, body.map(::clean).filter { it.isNotEmpty() })
        }
        return dedupe(cues)
    }

    /** YouTube's json3 caption format (`events` with `tStartMs`, `dDurationMs` and `segs[].utf8`). */
    fun parseJson3(text: String): List<TranscriptSegment> {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return emptyList()
        }
        val events = root["events"] as? JsonArray ?: return emptyList()
        val cues = mutableListOf<Cue>()
        for (el in events) {
            val ev = el as? JsonObject ?: continue
            val segs = ev["segs"] as? JsonArray ?: continue
            val start = (ev["tStartMs"]?.jsonPrimitive?.longOrNull ?: continue) / 1000.0
            val dur = (ev["dDurationMs"]?.jsonPrimitive?.longOrNull ?: 0L) / 1000.0
            val raw = segs.joinToString("") { (it as? JsonObject)?.get("utf8")?.jsonPrimitive?.contentOrNull.orEmpty() }
            cues += Cue(start, start + dur, raw.split('\n').map(::clean).filter { it.isNotEmpty() })
        }
        return dedupe(cues)
    }

    private val PASTED_TIME = Regex("""^\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\s*$""")
    private val PASTED_INLINE = Regex("""^\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\s+(\S.*)$""")

    /**
     * What YouTube's "Show transcript" gives when copied: a time on its own line, then the words ("0:15" / "Welcome back").
     * A time at the start of a line followed by words works too. Returns nothing unless at least three timed lines are found,
     * so a plain typed list is not mistaken for a transcript.
     */
    fun parsePasted(text: String): List<TranscriptSegment> {
        val out = mutableListOf<Pair<Double, StringBuilder>>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val only = PASTED_TIME.matchEntire(line)
            val inline = if (only == null) PASTED_INLINE.matchEntire(line) else null
            when {
                only != null -> out += time(only.groupValues[1], only.groupValues[2], only.groupValues[3], "0") to StringBuilder()
                inline != null -> out += time(inline.groupValues[1], inline.groupValues[2], inline.groupValues[3], "0") to StringBuilder(inline.groupValues[4])
                out.isNotEmpty() -> out.last().second.append(if (out.last().second.isEmpty()) "" else " ").append(line)
            }
        }
        val cleaned = out.map { it.first to clean(it.second.toString()) }.filter { it.second.isNotEmpty() }
        if (cleaned.size < 3) return emptyList()
        return cleaned.mapIndexed { i, (start, t) -> TranscriptSegment(start, cleaned.getOrNull(i + 1)?.first ?: (start + 5), t) }
    }

    /** Picks the parser from the file's start: a `{` is json3, anything else is read as WebVTT. */
    fun parse(text: String): List<TranscriptSegment> =
        if (text.trimStart().startsWith("{")) parseJson3(text) else parseVtt(text)

    private fun time(h: String, m: String, s: String, ms: String): Double =
        (h.toIntOrNull() ?: 0) * 3600.0 + m.toInt() * 60 + s.toInt() + ms.padEnd(3, '0').take(3).toInt() / 1000.0

    /**
     * Removes what a caption repeats. Rolling automatic captions put the last line of the previous cue at the top of the
     * next one, and add very short cues that only repeat text. For each cue, lines already shown by the cue before are
     * dropped; a cue whose text only extends the previous one keeps just the new words; equal neighbours are merged.
     */
    private fun dedupe(cues: List<Cue>): List<TranscriptSegment> {
        val out = mutableListOf<TranscriptSegment>()
        var prevLines: List<String> = emptyList()
        for (cue in cues.sortedBy { it.start }) {
            // A cue with no words at all (music, silence) does not stretch anything.
            if (cue.lines.isEmpty()) continue
            val fresh = cue.lines.filter { it !in prevLines }
            prevLines = cue.lines
            if (fresh.isEmpty()) {
                // Nothing new: stretch the previous segment to cover this cue.
                if (out.isNotEmpty() && cue.end > out.last().endSec) out[out.size - 1] = out.last().copy(endSec = cue.end)
                continue
            }
            var text = fresh.joinToString(" ")
            val last = out.lastOrNull()
            if (last != null) {
                if (text == last.text) {
                    out[out.size - 1] = last.copy(endSec = maxOf(last.endSec, cue.end))
                    continue
                }
                // The cue repeats the previous text and adds words: keep only the added words.
                if (text.startsWith(last.text + " ")) text = text.removePrefix(last.text + " ").trim()
            }
            if (text.isEmpty()) continue
            out += TranscriptSegment(cue.start, cue.end, text)
        }
        return out
    }
}
