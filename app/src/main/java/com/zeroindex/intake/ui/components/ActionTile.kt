package com.zeroindex.intake.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * One of the three equal primary actions (Search, Scan, Photo) on Nutrition and in Log food: a tile with a 40 dp icon tile above
 * its label. A `surface` tile on the ground; [selected] fills it ink, for the mode that is open.
 */
@Composable
fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = if (selected) scheme.primary else scheme.surface,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        shape = IntakeShape.TileLarge,
        modifier = modifier.heightIn(min = 88.dp),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // On the ink tile the icon tile turns lime, so it stays visible in both themes.
            IconTile(icon, style = if (selected) IconTileStyle.Lime else IconTileStyle.Soft)
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
