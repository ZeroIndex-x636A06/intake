package com.zeroindex.intake.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.EyebrowStyle
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.NumberSmall
import com.zeroindex.intake.ui.theme.intake

/** The uppercase micro-label above a heading (`.eyebrow`: 11.5 sp, +0.08 em, muted). */
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
 * The card the whole app is built from (`.card`): `surface` (white) fill on the ground, 24 dp corners, 18 dp padding, no border
 * and no shadow. Pass [color] for a variant; [HeroCard], [AccentCard] and [SoftCard] are the named ones.
 */
@Composable
fun IntakeCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    shape: Shape = IntakeShape.Card,
    contentPadding: PaddingValues = PaddingValues(IntakeSpacing.CardPad),
    content: @Composable () -> Unit,
) {
    Surface(
        color = color,
        contentColor = contentColor,
        shape = shape,
        // The card's clip comes before the caller's modifier, so a clickable card's ripple stays inside its corners.
        modifier = Modifier.clip(shape).then(modifier).fillMaxWidth(),
    ) {
        Column(Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * `.card.inkc`: the dark hero card (Today's ring and macro bars, the rest strip, the Maintenance tile). `hero` fill, `onHero`
 * text, 20 dp padding. Secondary text inside it is `onHero` at 72%; use [heroMuted].
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(IntakeSpacing.CardPadHero),
    content: @Composable () -> Unit,
) {
    IntakeCard(
        modifier = modifier,
        color = MaterialTheme.intake.hero,
        contentColor = MaterialTheme.intake.onHero,
        contentPadding = contentPadding,
        content = content,
    )
}

/** `.sub` / `.eyebrow` colour inside a hero card: the hero text colour at 72%. */
@Composable
fun heroMuted(): Color = MaterialTheme.intake.onHero.copy(alpha = 0.72f)

/** `.card.lime`: the one thing that needs attention (baseline week, weekly check-in, tip). Lime fill, `onAccent` text. */
@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(IntakeSpacing.CardPad),
    content: @Composable () -> Unit,
) {
    IntakeCard(
        modifier = modifier,
        color = MaterialTheme.intake.accent,
        contentColor = MaterialTheme.intake.onAccent,
        contentPadding = contentPadding,
        content = content,
    )
}

/** `.card.soft`: a quiet note card (the export reminder). */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(IntakeSpacing.CardPad),
    content: @Composable () -> Unit,
) {
    IntakeCard(
        modifier = modifier,
        color = MaterialTheme.intake.soft,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * `.list`: one card with no padding of its own, for stacked rows ([MenuItem], [SettingRow], or any row). Separate rows
 * with [ListDivider]. The rows are clipped to the 24 dp corners.
 */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IntakeCard(modifier = modifier, contentPadding = PaddingValues(0.dp), content = content)
}

/** `.li` divider: 1 dp `line`, between rows and never above the first. */
@Composable
fun ListDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** How an [IconTile] is filled (`.ic`, `.ic.lime`, `.ic.inkb`). */
enum class IconTileStyle { Soft, Lime, Ink }

/** `.ic`: a 40 dp tile (32 dp when [small]) holding a line icon. Decorative: the icon has no description. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    style: IconTileStyle = IconTileStyle.Soft,
    small: Boolean = false,
) {
    val (bg, fg) = when (style) {
        IconTileStyle.Soft -> MaterialTheme.intake.soft to MaterialTheme.colorScheme.onSurface
        IconTileStyle.Lime -> MaterialTheme.intake.accent to MaterialTheme.intake.onAccent
        IconTileStyle.Ink -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = if (small) IntakeShape.IconSmall else IntakeShape.Tile,
        modifier = modifier.size(if (small) 32.dp else 40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (small) IconSize.Small + 2.dp else IconSize.Default))
        }
    }
}

/**
 * A titled card (`.card` with a `.h2`). [caption] is the right-hand note: the range or window a card's figures cover, which
 * belongs next to the title rather than buried in the body.
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
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
 * Like [StatTile] but left-aligned, for the two-column week summary where the figures are read as a list rather than
 * compared across a row.
 */
@Composable
fun SummaryTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.intake.soft,
        shape = IntakeShape.InnerCard,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(IntakeSpacing.CardPadTile)) {
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
 * How much the figure above should be trusted. SPEC section 8 wants sparse data stated plainly rather than dressed up as a
 * confident number, so this sits directly under the number it qualifies.
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

/** A big value over a small uppercase caption on the `soft` fill (`.stat` tile). The value is a number: Space Grotesk. */
@Composable
fun StatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        color = MaterialTheme.intake.soft,
        shape = IntakeShape.InnerCard,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = NumberSmall,
                color = valueColor,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(1.dp))
            Eyebrow(caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The lime emphasis block for the remaining line and for reassurance copy (`.card.lime`): a bold lead followed by a sentence.
 * Pass [container] and [contentColor] for another pair.
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(text = lead, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.85f))
        }
    }
}

/** Vertical rhythm between stacked cards (`.stack`: 12 dp). */
val CardSpacing = Arrangement.spacedBy(IntakeSpacing.StackGap)
