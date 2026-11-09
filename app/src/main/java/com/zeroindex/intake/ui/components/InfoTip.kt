package com.zeroindex.intake.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * An (i) that explains a term in one sentence when tapped: "Effort", "Estimated max" and so on.
 * Put it next to a label; the sentence is also the accessibility description of the button, so
 * a screen reader user hears it without having to open anything.
 */
@Composable
fun InfoTip(
    text: String,
    modifier: Modifier = Modifier,
    label: String = "More information",
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "$label: $text",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 260.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** The one-sentence explanations shared by every screen that uses these words. */
object Glossary {
    const val MACROS = "Macros are the three nutrients that make up your calories: protein (builds and repairs muscle), carbs (quick energy) and fat (steady energy). Protein is the one to watch most."
    const val DAILY_BURN = "Daily calorie burn: roughly how many calories your body uses in a normal day, including movement."
    const val RESTING_BURN = "Calories at rest: what your body burns just keeping you alive, before any movement."
    const val AMRAP_LABEL = "As many as you can"
    const val EFFORT = "How hard a set felt, from 1 (very easy) to 10 (no reps left). 8 means about two more reps were possible."
    const val ESTIMATED_MAX = "The most you could lift once, worked out from your sets using the Epley formula. It is an estimate, not a test."
    const val AS_MANY_AS_YOU_CAN = "Keep going until you cannot do another clean rep, then log how many you did."
    const val DELOAD = "A lighter week with fewer sets and less weight, so your body can recover and come back stronger."
}
