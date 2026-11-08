package com.zeroindex.intake.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The mockup is noticeably rounder than stock Material 3 - cards at 22dp,
 * controls at 14-16dp - and that softness is most of its character, so the
 * whole shape scale moves rather than individual components overriding it.
 */
internal val IntakeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(13.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Named shapes for the pieces the mockup pins exactly. */
object IntakeShape {
    val Card = RoundedCornerShape(22.dp)
    val InnerCard = RoundedCornerShape(18.dp)
    val Tile = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(16.dp)
    val Field = RoundedCornerShape(15.dp)
    val Chip = RoundedCornerShape(14.dp)
    val Banner = RoundedCornerShape(18.dp)
    val Heat = RoundedCornerShape(6.dp)
    /** `.entry` and `.done` in the set grid: the tightest radius in the mockup. */
    val ExtraSmall = RoundedCornerShape(11.dp)
    /** The ordering quote block: square on the left where the rule sits. */
    val Quote = RoundedCornerShape(topEnd = 15.dp, bottomEnd = 15.dp)
}
