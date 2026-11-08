@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class)

package com.zeroindex.intake.ui.adaptive

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Amendment §3: the window size class, computed once in MainActivity and read
 * anywhere. Phones are Compact; a landscape tablet or a car head unit is
 * Expanded, which gets two panes and larger touch targets.
 */
val LocalWindowSizeClass = staticCompositionLocalOf { WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp)) }

val WindowSizeClass.isExpanded: Boolean get() = widthSizeClass == WindowWidthSizeClass.Expanded
val WindowSizeClass.isAtLeastMedium: Boolean get() = widthSizeClass != WindowWidthSizeClass.Compact
