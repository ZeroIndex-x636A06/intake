package com.zeroindex.intake.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Very round (DESIGN-V6 a.4): cards 24 dp, sheets 30 dp on top, buttons, chips, search and the dock full pills. The whole shape
 * scale moves, so stock Material components (sheets, dialogs, menus, text fields) pick it up without an override.
 */
internal val IntakeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/** Named shapes for the pieces the mockup pins exactly. */
object IntakeShape {
    /** Buttons, chips, pills, search field, segmented control, dock, avatar, switch, bars. */
    val Pill = CircleShape
    /** `.card`, `.list`. */
    val Card = RoundedCornerShape(24.dp)
    /** A tile inside a page: quick tiles, recipe card, Train tiles. */
    val TileLarge = RoundedCornerShape(20.dp)
    /** Day-strip button, limit boxes, macro tiles. */
    val InnerCard = RoundedCornerShape(18.dp)
    /** The 40 dp icon tile and the text input (14). */
    val Tile = RoundedCornerShape(14.dp)
    /** 32 dp icon tile. */
    val IconSmall = RoundedCornerShape(10.dp)
    val Button = Pill
    val Field = RoundedCornerShape(14.dp)
    val Input = Field
    val Chip = Pill
    val Banner = Card
    /** Image thumbnails. */
    val Thumb = RoundedCornerShape(16.dp)
    val Snack = RoundedCornerShape(16.dp)
    /** The live (session) pill. */
    val Live = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val Heat = RoundedCornerShape(6.dp)
    /** Set cell and tick in the set grid (a.4: 12). */
    val ExtraSmall = RoundedCornerShape(12.dp)
    /** A set row. */
    val SetRow = RoundedCornerShape(14.dp)
    /** Chart bars: 5 dp on top, 2 dp at the base. */
    val ChartBar = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
    /** The ordering quote block: square on the left where the rule sits. */
    val Quote = RoundedCornerShape(topEnd = 15.dp, bottomEnd = 15.dp)
}
