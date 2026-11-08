package com.zeroindex.intake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.BarSteel
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.PlateLarge
import com.zeroindex.intake.ui.theme.PlateSmall

/**
 * The mockup's `.plate-card`: a picture of one end of the loaded bar above the
 * same thing in words.
 *
 * The drawing is decoration, so the whole card is one accessibility node
 * carrying [description] - the sentence a screen reader should read, which is
 * the part that actually tells you what to load.
 */
@Composable
fun PlateVisual(
    title: String,
    perSide: List<Double>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val heaviest = perSide.maxOrNull() ?: 0.0

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = IntakeShape.Banner,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$title. $description" },
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarStub()
                perSide.forEach { plate ->
                    Spacer(Modifier.width(3.dp))
                    Plate(weightKg = plate, heaviestKg = heaviest)
                }
                Spacer(Modifier.width(3.dp))
                BarStub()
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BarStub() {
    Box(
        Modifier
            .width(43.dp)
            .height(8.dp)
            .background(BarSteel, RoundedCornerShape(5.dp)),
    )
}

/**
 * Plates are drawn in proportion to their weight so the stack reads at a
 * glance, and the heavier half takes the primary colour.
 */
@Composable
private fun Plate(weightKg: Double, heaviestKg: Double) {
    val ratio = if (heaviestKg > 0) (weightKg / heaviestKg).coerceIn(0.35, 1.0) else 1.0
    Box(
        Modifier
            .width((6 + 4 * ratio).dp)
            .height((14 + 14 * ratio).dp)
            .background(
                color = if (weightKg >= heaviestKg / 2) PlateLarge else PlateSmall,
                shape = RoundedCornerShape(4.dp),
            ),
    )
}
