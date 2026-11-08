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

/**
 * A titled card. [caption] is the mockup's `.chart-title` right-hand note -
 * the range or window a card's figures cover, which belongs next to the title
 * rather than buried in the body.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    IntakeCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
        content()
    }
}

/**
 * The mockup's `.summary-tile`: like [StatTile] but left-aligned, for the
 * two-column week summary where the figures are read as a list rather than
 * compared across a row.
 */
@Composable
fun SummaryTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = IntakeShape.Tile,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Eyebrow(caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The mockup's `.confidence` line: how much the figure above it should be
 * trusted. SPEC §8 wants sparse data stated plainly rather than dressed up as
 * a confident number, so this sits directly under the number it qualifies.
 */
@Composable
fun ConfidenceLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "◉  $text",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
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
