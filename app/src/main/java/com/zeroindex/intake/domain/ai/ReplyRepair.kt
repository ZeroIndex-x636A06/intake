package com.zeroindex.intake.domain.ai

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The result of reading a model's reply against a schema. */
sealed interface ReplyDecode<out T> {
    data class Ok<T>(val value: T) : ReplyDecode<T>

    /** [error] is short and safe to hand back to a model in a repair prompt; it is never shown to the user. */
    data class Invalid(val error: String) : ReplyDecode<Nothing>
}

/**
 * Reads what a model says into a typed value, forgiving the harmless ways models stray from "JSON only":
 * a markdown fence around the object, a sentence before or after it, and a trailing comma. Anything that is
 * still not valid after that is reported as [ReplyDecode.Invalid], never guessed at.
 *
 * Pure and Android-free, shared by the gateway and the benchmark so "parsed" means the same in both.
 */
object ReplyRepair {

    private const val MAX_ERROR = 300

    /** A byte order mark some models put first; built from its code so no literal U+FEFF is in the source. */
    private val BOM: String = 0xFEFF.toChar().toString()

    /**
     * The outermost JSON object in [content], with fences, surrounding prose and trailing commas removed, or
     * null when there is no complete object (for example a reply cut off by the token limit).
     */
    fun extractJson(content: String): String? {
        val text = stripFence(content.replace(BOM, "")).trim()
        val start = text.indexOf('{')
        if (start < 0) return null
        val end = matchingBrace(text, start) ?: return null
        return removeTrailingCommas(text.substring(start, end + 1))
    }

    fun <T> decode(json: Json, serializer: KSerializer<T>, content: String): ReplyDecode<T> {
        val text = extractJson(content)
            ?: return ReplyDecode.Invalid(
                if ('{' in content) "the JSON object was cut off or unbalanced" else "there was no JSON object in the reply",
            )
        val lenient = Json(from = json) {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        return try {
            ReplyDecode.Ok(lenient.decodeFromString(serializer, text))
        } catch (e: SerializationException) {
            ReplyDecode.Invalid(clean(e.message))
        } catch (e: IllegalArgumentException) {
            ReplyDecode.Invalid(clean(e.message))
        }
    }

    /** The text inside the first ``` fence when there is one (and a brace inside it), otherwise the input. */
    private fun stripFence(s: String): String {
        val open = s.indexOf("```")
        if (open < 0) return s
        val bodyStart = s.indexOf('\n', open).let { if (it < 0) open + 3 else it + 1 }
        val close = s.indexOf("```", bodyStart)
        val inside = if (close < 0) s.substring(bodyStart) else s.substring(bodyStart, close)
        return if ('{' in inside) inside else s
    }

    /** Index of the brace closing the one at [start], skipping strings and escapes; null if never closed. */
    private fun matchingBrace(s: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    /** Drops a comma that is followed only by whitespace and a closing brace or bracket, outside strings. */
    private fun removeTrailingCommas(s: String): String {
        val out = StringBuilder(s.length)
        var inString = false
        var escaped = false
        for (i in s.indices) {
            val c = s[i]
            if (inString) {
                out.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            if (c == '"') {
                inString = true
                out.append(c)
            } else if (c == ',') {
                var j = i + 1
                while (j < s.length && s[j].isWhitespace()) j++
                val trailing = j < s.length && (s[j] == '}' || s[j] == ']')
                if (!trailing) out.append(c)
            } else {
                out.append(c)
            }
        }
        return out.toString()
    }

    private fun clean(message: String?): String =
        (message ?: "the reply did not match the schema").lineSequence().first().take(MAX_ERROR)
}
