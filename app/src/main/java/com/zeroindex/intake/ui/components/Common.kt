package com.zeroindex.intake.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.EyebrowStyle
import com.zeroindex.intake.ui.theme.IntakeShape

/** The mockup's uppercase micro-label above a heading. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text.uppercase(),
        style = EyebrowStyle,
        color = color,
        modifier = modifier,
    )
}

/**
 * The card the whole mockup is built from: surface fill, hairline outline,
 * 22dp corners.
 */
@Composable
fun IntakeCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Surface(
        color = color,
        shape = IntakeShape.Card,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(15.dp)) { content() }
    }
}

/** A titled card, with the optional trailing action the mockup's section lines have. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    IntakeCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        content()
    }
}

/**
 * The mockup's `.macro` and `.stat` tile: a big value over a small uppercase
 * caption, on the variant fill.
 */
@Composable
fun StatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = IntakeShape.Tile,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(1.dp))
            Eyebrow(caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The pale-green emphasis block the mockup uses for the remaining line and for
 * reassurance copy: a bold lead followed by a sentence.
 */
@Composable
fun AccentNote(
    lead: String,
    body: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        color = container,
        contentColor = contentColor,
        shape = IntakeShape.Banner,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Text(text = lead, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(text = body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Vertical rhythm between stacked cards in the mockup. */
val CardSpacing = Arrangement.spacedBy(12.dp)
