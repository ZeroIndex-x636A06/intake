package com.zeroindex.intake.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The Intake line-icon set (DESIGN-V6 a.6): outline icons on a 24 dp viewBox, stroke 1.8, round caps and joins, no fill. The path
 * data is the mockup's own inline SVG (the `.dc.html` files in design/mockup-v6/source, `<svg class="i">`); circles, ellipses and rects were
 * converted to arcs, nothing else was changed. Built lazily from the path strings at first use, so there is no extra resource
 * and no dependency beyond Compose.
 *
 * Use with `Icon(IntakeIcons.Plus, contentDescription = null, modifier = Modifier.size(IconSize.Default))`. The vector is drawn
 * black and is meant to be tinted: [androidx.compose.material3.Icon] tints it with the content colour.
 * Sizes: [IconSize].
 */
object IntakeIcons {
    private val Ink = SolidColor(Color.Black)

    /** Each pair is (SVG path data, filled). Filled shapes are the mockup's rare `fill="currentColor"` ones. */
    private fun line(name: String, vararg paths: Pair<String, Boolean>): ImageVector {
        val b = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { (d, filled) ->
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (filled) Ink else null,
                stroke = Ink,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val ChevronDown: ImageVector by lazy { line("ChevronDown", "M6 9l6 6 6-6" to false) }
    val ChevronUp: ImageVector by lazy { line("ChevronUp", "M6 15l6-6 6 6" to false) }
    val ChevronLeft: ImageVector by lazy { line("ChevronLeft", "M15 6l-6 6 6 6" to false) }
    val ChevronRight: ImageVector by lazy { line("ChevronRight", "M9 6l6 6-6 6" to false) }
    val More: ImageVector by lazy { line("More", "M3.7 12 a1.3 1.3 0 1 0 2.6 0 a1.3 1.3 0 1 0 -2.6 0" to false, "M10.7 12 a1.3 1.3 0 1 0 2.6 0 a1.3 1.3 0 1 0 -2.6 0" to false, "M17.7 12 a1.3 1.3 0 1 0 2.6 0 a1.3 1.3 0 1 0 -2.6 0" to false) }
    val Check: ImageVector by lazy { line("Check", "M5 12l5 5 9-10" to false) }
    val Barbell: ImageVector by lazy { line("Barbell", "M3 12h3M18 12h3M6 8v8M18 8v8M9 6v12M15 6v12M9 12h6" to false) }
    val Plus: ImageVector by lazy { line("Plus", "M12 5v14M5 12h14" to false) }
    val Minus: ImageVector by lazy { line("Minus", "M5 12h14" to false) }
    val ArrowRight: ImageVector by lazy { line("ArrowRight", "M5 12h14M13 6l6 6-6 6" to false) }
    val ArrowDown: ImageVector by lazy { line("ArrowDown", "M12 4v16M6 14l6 6 6-6" to false) }
    val ArrowUpBar: ImageVector by lazy { line("ArrowUpBar", "M12 20V6M7 11l5-5 5 5M5 3h14" to false) }
    val Download: ImageVector by lazy { line("Download", "M12 3v12M7 10l5 5 5-5M4 20h16" to false) }
    val Upload: ImageVector by lazy { line("Upload", "M12 21V9M7 14l5-5 5 5M4 3h16" to false) }
    val Bolt: ImageVector by lazy { line("Bolt", "M13 2L4 14h7l-1 8 9-12h-7z" to false) }
    val ShieldAlert: ImageVector by lazy { line("ShieldAlert", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z" to false, "M12 8v5M12 16v.5" to false) }
    val ShieldCheck: ImageVector by lazy { line("ShieldCheck", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z" to false, "M8.5 12l2.5 2.5 4.5-5" to false) }
    val Shield: ImageVector by lazy { line("Shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z" to false) }
    val Search: ImageVector by lazy { line("Search", "M4 11 a7 7 0 1 0 14 0 a7 7 0 1 0 -14 0" to false, "M20 20l-3.5-3.5" to false) }
    val SearchPlus: ImageVector by lazy { line("SearchPlus", "M4 11 a7 7 0 1 0 14 0 a7 7 0 1 0 -14 0" to false, "M20 20l-3.5-3.5M8 11h6M11 8v6" to false) }
    val Book: ImageVector by lazy { line("Book", "M4 5a2 2 0 0 1 2-2h12v18H6a2 2 0 0 1-2-2z" to false, "M8 7h6M8 11h6" to false) }
    val Close: ImageVector by lazy { line("Close", "M6 6l12 12M18 6L6 18" to false) }
    val ListBullets: ImageVector by lazy { line("ListBullets", "M8 6h12M8 12h12M8 18h12M4 6h.5M4 12h.5M4 18h.5" to false) }
    val Timer: ImageVector by lazy { line("Timer", "M4 13 a8 8 0 1 0 16 0 a8 8 0 1 0 -16 0" to false, "M12 9v4l2 2M9 2h6" to false) }
    val Clock: ImageVector by lazy { line("Clock", "M3 12 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0" to false, "M12 7v5l3 2" to false) }
    val Target: ImageVector by lazy { line("Target", "M3 12 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0" to false, "M8 12 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0" to false) }
    val Drop: ImageVector by lazy { line("Drop", "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z" to false) }
    val Mug: ImageVector by lazy { line("Mug", "M5 8h12v6a5 5 0 0 1-5 5h-2a5 5 0 0 1-5-5zM17 10h2a2 2 0 0 1 0 4h-2" to false) }
    val Cup: ImageVector by lazy { line("Cup", "M6 3h12l-1.5 17a1 1 0 0 1-1 1h-7a1 1 0 0 1-1-1z" to false) }
    val CupFilled: ImageVector by lazy { line("CupFilled", "M6 3h12l-1.5 17a1 1 0 0 1-1 1h-7a1 1 0 0 1-1-1z" to false, "M6.5 8h11" to false) }
    val Notes: ImageVector by lazy { line("Notes", "M6 4 H18 a3 3 0 0 1 3 3 V18 a3 3 0 0 1 -3 3 H6 a3 3 0 0 1 -3 -3 V7 a3 3 0 0 1 3 -3 z" to false, "M3 9h18M8 13h3M8 17h6" to false) }
    val Image: ImageVector by lazy { line("Image", "M6 5 H18 a3 3 0 0 1 3 3 V16 a3 3 0 0 1 -3 3 H6 a3 3 0 0 1 -3 -3 V8 a3 3 0 0 1 3 -3 z" to false, "M7 10 a2 2 0 1 0 4 0 a2 2 0 1 0 -4 0" to false, "M21 16l-5-5-8 8" to false) }
    val Pencil: ImageVector by lazy { line("Pencil", "M4 20h4L19 9l-4-4L4 16z" to false) }
    val Trash: ImageVector by lazy { line("Trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" to false) }
    val Swap: ImageVector by lazy { line("Swap", "M7 4L3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7" to false) }
    val Bulb: ImageVector by lazy { line("Bulb", "M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5c.7.7 1 1.5 1 2.5h6c0-1 .3-1.8 1-2.5A6 6 0 0 0 12 3z" to false) }
    val Calendar: ImageVector by lazy { line("Calendar", "M6 4 H18 a3 3 0 0 1 3 3 V18 a3 3 0 0 1 -3 3 H6 a3 3 0 0 1 -3 -3 V7 a3 3 0 0 1 3 -3 z" to false, "M3 9h18M8 2v4M16 2v4" to false) }
    val CalendarPlain: ImageVector by lazy { line("CalendarPlain", "M6 4 H18 a3 3 0 0 1 3 3 V18 a3 3 0 0 1 -3 3 H6 a3 3 0 0 1 -3 -3 V7 a3 3 0 0 1 3 -3 z" to false, "M3 9h18" to false) }
    val ForkKnife: ImageVector by lazy { line("ForkKnife", "M7 2v20M4 2v6a3 3 0 0 0 6 0V2M17 22V2c-2 0-3.5 3-3.5 7s1.5 4 3.5 4" to false) }
    val Dumbbell: ImageVector by lazy { line("Dumbbell", "M6 7v10M3 9.5v5M18 7v10M21 9.5v5M6 12h12" to false) }
    val LineChart: ImageVector by lazy { line("LineChart", "M3 20h18M6 16l4-5 4 3 5-7" to false) }
    val Signal: ImageVector by lazy { line("Signal", "M6 8a6 6 0 0 1 0 8M10 6a9 9 0 0 1 0 12M14 4a12 12 0 0 1 0 16" to false) }
    val Clipboard: ImageVector by lazy { line("Clipboard", "M7 3 H17 a3 3 0 0 1 3 3 V18 a3 3 0 0 1 -3 3 H7 a3 3 0 0 1 -3 -3 V6 a3 3 0 0 1 3 -3 z" to false, "M8 8h8M8 12h8M8 16h5" to false) }
    val Cart: ImageVector by lazy { line("Cart", "M3 4h2l2.5 11h10L20 7H6.5" to false, "M7.5 19 a1.5 1.5 0 1 0 3 0 a1.5 1.5 0 1 0 -3 0" to false, "M15.5 19 a1.5 1.5 0 1 0 3 0 a1.5 1.5 0 1 0 -3 0" to false) }
    val MapPin: ImageVector by lazy { line("MapPin", "M12 21s7-6 7-12a7 7 0 0 0-14 0c0 6 7 12 7 12z" to false, "M9.5 9 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0" to false) }
    val FileText: ImageVector by lazy { line("FileText", "M6 3h9l4 4v14H6z" to false, "M14 3v5h5M9 12h6M9 16h6" to false) }
    val File: ImageVector by lazy { line("File", "M6 3h9l4 4v14H6z" to false, "M14 3v5h5" to false) }
    val Bowl: ImageVector by lazy { line("Bowl", "M3 12h18a9 9 0 0 1-18 0zM8 8c0-2 2-2 2-4M13 8c0-2 2-2 2-4" to false) }
    val Share: ImageVector by lazy { line("Share", "M15.5 5 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0" to false, "M3.5 12 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0" to false, "M15.5 19 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0" to false, "M8.2 10.8l7.6-4.4M8.2 13.2l7.6 4.4" to false) }
    val User: ImageVector by lazy { line("User", "M8 8 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0" to false, "M4 21c1-4 4-6 8-6s7 2 8 6" to false) }
    val Scan: ImageVector by lazy { line("Scan", "M4 7V5a1 1 0 0 1 1-1h2M17 4h2a1 1 0 0 1 1 1v2M20 17v2a1 1 0 0 1-1 1h-2M7 20H5a1 1 0 0 1-1-1v-2M8 8v8M11 8v8M14 8v8M17 8v8" to false) }
    val Camera: ImageVector by lazy { line("Camera", "M6 7 H18 a3 3 0 0 1 3 3 V17 a3 3 0 0 1 -3 3 H6 a3 3 0 0 1 -3 -3 V10 a3 3 0 0 1 3 -3 z" to false, "M8.5 13.5 a3.5 3.5 0 1 0 7 0 a3.5 3.5 0 1 0 -7 0" to false, "M9 7l1.5-3h3L15 7" to false) }
    val Flame: ImageVector by lazy { line("Flame", "M12 3c1 4 6 5 6 11a6 6 0 0 1-12 0c0-3 2-5 3-6 0 2 1 3 2 3 0-3-1-5 1-8z" to false) }
    val Scale: ImageVector by lazy { line("Scale", "M7 4 H17 a4 4 0 0 1 4 4 V16 a4 4 0 0 1 -4 4 H7 a4 4 0 0 1 -4 -4 V8 a4 4 0 0 1 4 -4 z" to false, "M9 10a3 3 0 0 1 6 0M12 10l1.5-2" to false) }
    val Tape: ImageVector by lazy { line("Tape", "M3 8h18v8H3z" to false, "M7 8v3M11 8v4M15 8v3M19 8v4" to false) }
    val Leaf: ImageVector by lazy { line("Leaf", "M12 21c-5 0-8-3-8-7 0-3 2-5 4-6 1-3 3-5 4-5s3 2 4 5c2 1 4 3 4 6 0 4-3 7-8 7z" to false) }
    val Notepad: ImageVector by lazy { line("Notepad", "M8 4 H16 a4 4 0 0 1 4 4 V16 a4 4 0 0 1 -4 4 H8 a4 4 0 0 1 -4 -4 V8 a4 4 0 0 1 4 -4 z" to false, "M8 9h8M8 13h8M8 17h5" to false) }
    val Gauge: ImageVector by lazy { line("Gauge", "M12 3a9 9 0 1 0 9 9" to false, "M21 3l-9 9" to false) }
    val Dashboard: ImageVector by lazy { line("Dashboard", "M5.5 3 H8.5 a2.5 2.5 0 0 1 2.5 2.5 V10.5 a2.5 2.5 0 0 1 -2.5 2.5 H5.5 a2.5 2.5 0 0 1 -2.5 -2.5 V5.5 a2.5 2.5 0 0 1 2.5 -2.5 z" to false, "M15.5 3 H18.5 a2.5 2.5 0 0 1 2.5 2.5 V6.5 a2.5 2.5 0 0 1 -2.5 2.5 H15.5 a2.5 2.5 0 0 1 -2.5 -2.5 V5.5 a2.5 2.5 0 0 1 2.5 -2.5 z" to false, "M15.5 11 H18.5 a2.5 2.5 0 0 1 2.5 2.5 V18.5 a2.5 2.5 0 0 1 -2.5 2.5 H15.5 a2.5 2.5 0 0 1 -2.5 -2.5 V13.5 a2.5 2.5 0 0 1 2.5 -2.5 z" to false, "M5.5 15 H8.5 a2.5 2.5 0 0 1 2.5 2.5 V18.5 a2.5 2.5 0 0 1 -2.5 2.5 H5.5 a2.5 2.5 0 0 1 -2.5 -2.5 V17.5 a2.5 2.5 0 0 1 2.5 -2.5 z" to false) }
    val Settings: ImageVector by lazy { line("Settings", "M9 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0" to false, "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9 7 7M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1" to false) }
    val Sparkle: ImageVector by lazy { line("Sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z" to false) }
    val Link: ImageVector by lazy { line("Link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" to false) }
    val Lines: ImageVector by lazy { line("Lines", "M5 6h14M5 12h14M5 18h9" to false) }
    val Heart: ImageVector by lazy { line("Heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z" to false) }
    val Compare: ImageVector by lazy { line("Compare", "M9 6l-6 6 6 6M15 6l6 6-6 6" to false) }
    val Sliders: ImageVector by lazy { line("Sliders", "M4 6h10M4 12h16M4 18h7M18 4v4M16 6h4" to false) }
    val Grid: ImageVector by lazy { line("Grid", "M5 3 H9 a2 2 0 0 1 2 2 V9 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 V5 a2 2 0 0 1 2 -2 z" to false, "M15 3 H19 a2 2 0 0 1 2 2 V9 a2 2 0 0 1 -2 2 H15 a2 2 0 0 1 -2 -2 V5 a2 2 0 0 1 2 -2 z" to false, "M5 13 H9 a2 2 0 0 1 2 2 V19 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 V15 a2 2 0 0 1 2 -2 z" to false, "M15 13 H19 a2 2 0 0 1 2 2 V19 a2 2 0 0 1 -2 2 H15 a2 2 0 0 1 -2 -2 V15 a2 2 0 0 1 2 -2 z" to false) }
    val Code: ImageVector by lazy { line("Code", "M8 7l-5 5 5 5M16 7l5 5-5 5" to false) }
    val External: ImageVector by lazy { line("External", "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" to false) }
    val Theme: ImageVector by lazy { line("Theme", "M4 12 a8 8 0 1 0 16 0 a8 8 0 1 0 -16 0" to false, "M12 4v16" to false, "M12 4a8 8 0 0 1 0 16z" to true) }
    val Bell: ImageVector by lazy { line("Bell", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 21h4" to false) }
    val Database: ImageVector by lazy { line("Database", "M4 6 a8 3 0 1 0 16 0 a8 3 0 1 0 -16 0" to false, "M4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3" to false) }
    val Video: ImageVector by lazy { line("Video", "M6 5 H18 a3 3 0 0 1 3 3 V16 a3 3 0 0 1 -3 3 H6 a3 3 0 0 1 -3 -3 V8 a3 3 0 0 1 3 -3 z" to false, "M10 9l5 3-5 3z" to false) }
    val Sync: ImageVector by lazy { line("Sync", "M4 12a8 8 0 0 1 14-5.3M20 12a8 8 0 0 1-14 5.3M18 3v4h-4M6 21v-4h4" to false) }
    val Key: ImageVector by lazy { line("Key", "M4 15 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0" to false, "M11 12l9-9M17 6l3 3M15 8l2 2" to false) }
    val Help: ImageVector by lazy { line("Help", "M3 12 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0" to false, "M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.7M12 17v.5" to false) }
    val Info: ImageVector by lazy { line("Info", "M3 12 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0" to false, "M12 8v5M12 16v.5" to false) }
    val Play: ImageVector by lazy { line("Play", "M7 4l13 8-13 8z" to false) }
    val PlayAlt: ImageVector by lazy { line("PlayAlt", "M8 5l11 7-11 7z" to false) }
    val Bookmark: ImageVector by lazy { line("Bookmark", "M6 3h12v18l-6-4-6 4z" to false) }
    // Video player controls (filled play and pause, so the big centre button reads at a glance).
    val PlayFilled: ImageVector by lazy { line("PlayFilled", "M8 5l11 7-11 7z" to true) }
    val PauseFilled: ImageVector by lazy { line("PauseFilled", "M7.5 5h3v14h-3z" to true, "M13.5 5h3v14h-3z" to true) }
    val Rewind: ImageVector by lazy { line("Rewind", "M11 6l-6 6 6 6M19 6l-6 6 6 6" to false) }
    val FastForward: ImageVector by lazy { line("FastForward", "M13 6l6 6-6 6M5 6l6 6-6 6" to false) }
    val Fullscreen: ImageVector by lazy { line("Fullscreen", "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" to false) }
    val FullscreenExit: ImageVector by lazy { line("FullscreenExit", "M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5" to false) }
    val Loop: ImageVector by lazy { line("Loop", "M17 2l3 3-3 3M4 11V9a4 4 0 0 1 4-4h12M7 22l-3-3 3-3M20 13v2a4 4 0 0 1-4 4H4" to false) }
    val History: ImageVector by lazy { line("History", "M4 12a8 8 0 1 0 2.3-5.7M4 4v4h4" to false) }
    val Trophy: ImageVector by lazy { line("Trophy", "M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4a3 3 0 0 0 3 4M17 6h3a3 3 0 0 1-3 4" to false) }
    val BackCircle: ImageVector by lazy { line("BackCircle", "M3 12 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0" to false, "M12 8l-4 4 4 4M8 12h8" to false) }
}

/** Icon sizes of the mockup: `.i` 22, `.l` 28, `.s` 16, `.xs` 14. */
object IconSize {
    val Default = 22.dp
    val Large = 28.dp
    val Small = 16.dp
    val ExtraSmall = 14.dp
}
