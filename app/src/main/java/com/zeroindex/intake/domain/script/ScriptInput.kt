package com.zeroindex.intake.domain.script

/** Helpers for the import screens: what kind of text was picked, and how to show a problem. */
object ScriptInput {

    /** A first non-blank character of `{` means the old JSON plan file; anything else is a script. */
    fun isJson(text: String): Boolean =
        text.removePrefix("\uFEFF").firstOrNull { !it.isWhitespace() } == '{'

    /** "Line 14, column 3: message" */
    fun describe(d: Diagnostic): String = "Line ${d.line}, column ${d.column}: ${d.message}"
}
