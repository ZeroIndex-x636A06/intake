package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Liquor
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material.icons.filled.Blender
import androidx.compose.ui.graphics.vector.ImageVector
import com.zeroindex.intake.domain.hydration.DrinkType

/** One icon per drink type, for the chips on the hydration card and in the sheet. */
fun DrinkType.icon(): ImageVector = when (this) {
    DrinkType.WATER -> com.zeroindex.intake.ui.theme.IntakeIcons.Drop
    DrinkType.SPARKLING -> Icons.Filled.BubbleChart
    DrinkType.TEA -> com.zeroindex.intake.ui.theme.IntakeIcons.Mug
    DrinkType.COFFEE -> com.zeroindex.intake.ui.theme.IntakeIcons.Mug
    DrinkType.MILK -> com.zeroindex.intake.ui.theme.IntakeIcons.Drop
    DrinkType.JUICE -> Icons.Filled.Blender
    DrinkType.SODA -> Icons.Filled.Fastfood
    DrinkType.SPORTS_DRINK -> com.zeroindex.intake.ui.theme.IntakeIcons.Dumbbell
    DrinkType.ORS -> Icons.Filled.MedicalServices
    DrinkType.BEER -> com.zeroindex.intake.ui.theme.IntakeIcons.Cup
    DrinkType.WINE -> Icons.Filled.WineBar
    DrinkType.SPIRITS -> Icons.Filled.Liquor
    DrinkType.CUSTOM -> Icons.Filled.Tune
}
