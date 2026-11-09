package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Liquor
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.SportsBar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material.icons.filled.Blender
import androidx.compose.ui.graphics.vector.ImageVector
import com.zeroindex.intake.domain.hydration.DrinkType

/** One icon per drink type, for the chips on the hydration card and in the sheet. */
fun DrinkType.icon(): ImageVector = when (this) {
    DrinkType.WATER -> Icons.Filled.WaterDrop
    DrinkType.SPARKLING -> Icons.Filled.BubbleChart
    DrinkType.TEA -> Icons.Filled.EmojiFoodBeverage
    DrinkType.COFFEE -> Icons.Filled.Coffee
    DrinkType.MILK -> Icons.Filled.LocalDrink
    DrinkType.JUICE -> Icons.Filled.Blender
    DrinkType.SODA -> Icons.Filled.Fastfood
    DrinkType.SPORTS_DRINK -> Icons.Filled.FitnessCenter
    DrinkType.ORS -> Icons.Filled.MedicalServices
    DrinkType.BEER -> Icons.Filled.SportsBar
    DrinkType.WINE -> Icons.Filled.WineBar
    DrinkType.SPIRITS -> Icons.Filled.Liquor
    DrinkType.CUSTOM -> Icons.Filled.Tune
}
