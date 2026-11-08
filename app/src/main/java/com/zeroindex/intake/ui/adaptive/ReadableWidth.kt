package com.zeroindex.intake.ui.adaptive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val ReadableMaxWidth = 720.dp

/**
 * Amendment §3: single-column screens (forms, lists, settings) would stretch
 * their text across a 1200dp head unit, which is hard to read. This caps the
 * content at a comfortable line length and centres it; below the cap it fills
 * the width exactly as before, so phones are unaffected. Two-pane screens use
 * the full width instead and must not apply it.
 */
fun Modifier.readableWidth(): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = ReadableMaxWidth)
        .fillMaxWidth()
