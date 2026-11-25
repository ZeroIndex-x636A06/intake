package com.zeroindex.intake.domain.videos

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One caption line with its time. Seconds are from the start of the video. */
@Serializable
data class TranscriptSegment(val startSec: Double, val endSec: Double, val text: String)

/** The captions of one video, read from a caption file. [auto] is true for YouTube's automatic captions. */
@Serializable
data class Transcript(
    val videoId: String,
    val language: String,
    val auto: Boolean,
    val segments: List<TranscriptSegment>,
) {
    val isEmpty: Boolean get() = segments.isEmpty()

    /** The whole text, one segment a line, no times (what the line parsers and a paste box read). */
    fun plainText(): String = segments.joinToString("\n") { it.text }

    /** Text for a model: `[m:ss] words` per line, trimmed to [maxChars] (whole lines, an even spread when it is long). */
    fun timedText(maxChars: Int): String = TranscriptTrim.timed(segments, maxChars)

    fun encode(): String = Json.encodeToString(this)

    companion object {
        fun decode(json: String): Transcript? = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<Transcript>(json)
        } catch (e: Exception) {
            null
        }
    }
}

/** Keeps a timed transcript inside a character budget without cutting a line in half. */
object TranscriptTrim {
    /** Neighbouring caption lines are joined into runs of about this many seconds, so every timestamp stands for a sentence. */
    private const val RUN_SEC = 6.0

    fun timed(segments: List<TranscriptSegment>, maxChars: Int): String {
        val lines = runs(segments).map { "[${YoutubeIds.clock(it.startSec.toInt())}] ${it.text}" }
        val total = lines.sumOf { it.length + 1 }
        if (total <= maxChars) return lines.joinToString("\n")
        // Too long: keep an even spread of whole lines, so the end of the video is not lost.
        val keepEvery = kotlin.math.ceil(total.toDouble() / maxChars).toInt().coerceAtLeast(2)
        val kept = lines.filterIndexed { i, _ -> i % keepEvery == 0 }
        val out = StringBuilder()
        for (l in kept) {
            if (out.length + l.length + 1 > maxChars) break
            if (out.isNotEmpty()) out.append('\n')
            out.append(l)
        }
        return out.toString()
    }

    internal fun runs(segments: List<TranscriptSegment>): List<TranscriptSegment> {
        val out = mutableListOf<TranscriptSegment>()
        var cur: TranscriptSegment? = null
        for (s in segments) {
            val c = cur
            if (c == null) {
                cur = s
            } else if (s.startSec - c.startSec < RUN_SEC && c.text.length < 160) {
                cur = c.copy(endSec = s.endSec, text = c.text + " " + s.text)
            } else {
                out += c
                cur = s
            }
        }
        cur?.let { out += it }
        return out
    }
}
