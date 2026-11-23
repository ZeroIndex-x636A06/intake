package com.zeroindex.intake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeElevation
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.NumberSmall
import com.zeroindex.intake.ui.theme.intake

/**
 * Shared controls (DESIGN-V6 b.2, b.3, b.6). Every interactive one is at least 44 dp tall (a.3), taller as the font scale grows.
 */

/** The in-screen title and its one explanatory line. */
@Composable
fun SubHead(
    title: String,
    subcopy: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subcopy != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = subcopy,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Just the explanatory line of a [SubHead], for a pushed screen whose top bar already shows the title. */
@Composable
fun SubCopy(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
    )
}

/**
 * `.sec`: a bare heading row between cards, with an optional trailing action or note. Unlike [SectionCard] this draws no
 * container.
 */
@Composable
fun SectionLine(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = IntakeSpacing.SectionTop + 4.dp, bottom = 8.dp, start = IntakeSpacing.SectionSide, end = IntakeSpacing.SectionSide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The title never shares a line with the caption, so it cannot be squeezed into breaking mid-word.
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * `.seg`: the inline two- to four-way switch (units, theme, Diary / Plan / Recipes). A `soft` pill track with the selected item
 * lifted on a `surface` pill. It scrolls sideways instead of truncating when the options do not fit (font scale 1.5).
 */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** [Role.Tab] when the control switches a screen's pages (Train); [Role.RadioButton] for a setting. */
    role: Role = Role.RadioButton,
) {
    Surface(
        color = MaterialTheme.intake.soft,
        shape = IntakeShape.Pill,
        modifier = modifier,
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = IntakeShape.Pill,
                    shadowElevation = if (selected) IntakeElevation.SelectedSegment else 0.dp,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        // Clip before the click so the press ripple takes the pill's round corners.
                        .clip(IntakeShape.Pill)
                        .clickable(
                            role = role,
                            onClickLabel = option,
                            onClick = { onSelect(index) },
                        )
                        .semantics { this.selected = selected },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = option, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** `.pill` / `.pill.on`: a filter choice. Unselected is `soft`, selected is the ink pill. 44 dp tall. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.intake.soft,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.Chip,
        modifier = modifier
            .heightIn(min = IntakeSpacing.TouchTarget)
            // Clip before the click so the press ripple follows the chip's shape.
            .clip(IntakeShape.Chip)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The tones of [IntakeChip] (`.chip`, `.chip.ok` ...): one status language. */
enum class ChipTone {
    /** `soft` / ink: default, filters, "Only the name was checked", "Checking...". Never a pass. */
    Neutral,
    /** `okBg` / `ok`: "Fits your allergens", Applied, Ready. */
    Ok,
    /** `warnBg` / `warn`: May contain, Check the label, Not checked, Due. */
    Warn,
    /** `badBg` / `bad`: Contains, Blocked, Failed. */
    Bad,
    /** `aiBg` / `ai`: every AI label. */
    Ai,
    /** `ink` / `inkOn`: selected small filter, counts. */
    Ink,
    /** `accent` / `onAccent`: "Planned" and highlights. */
    Lime,
    /** Transparent with a 1.5 dp `line` outline. */
    Outline,
}

/**
 * `.chip`: a pill, 28 dp tall, 12.5 sp, with an optional 14 dp leading [icon]. Words always carry the meaning, so colour is
 * never the only signal. [small] is the 20 dp AI form. With [onClick] the hit area is 44 dp.
 */
@Composable
fun IntakeChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.Neutral,
    icon: ImageVector? = null,
    small: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = MaterialTheme.intake
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        ChipTone.Neutral -> c.soft to scheme.onSurface
        ChipTone.Ok -> c.okBg to c.ok
        ChipTone.Warn -> c.warnBg to c.warn
        ChipTone.Bad -> c.badBg to c.bad
        ChipTone.Ai -> c.aiBg to c.ai
        ChipTone.Ink -> scheme.primary to scheme.onPrimary
        ChipTone.Lime -> c.accent to c.onAccent
        ChipTone.Outline -> Color.Transparent to scheme.onSurface
    }
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .heightIn(min = if (small) 20.dp else 28.dp)
                .padding(horizontal = if (small) 6.dp else 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.ExtraSmall))
            Text(
                text = text,
                style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
    val outline = if (tone == ChipTone.Outline) {
        androidx.compose.foundation.BorderStroke(1.5.dp, scheme.outlineVariant)
    } else {
        null
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            color = bg,
            contentColor = fg,
            shape = IntakeShape.Chip,
            border = outline,
            // A 28 dp chip keeps a 44 dp hit area.
            modifier = modifier.minimumInteractiveComponentSize(),
            content = body,
        )
    } else {
        Surface(color = bg, contentColor = fg, shape = IntakeShape.Chip, border = outline, modifier = modifier, content = body)
    }
}

/**
 * A filled block that carries an eyebrow, a heading and a line of detail (the active program, a hero note). The default is the
 * hero card: `hero` fill with `onHero` text, ink in light and a separate near-black in dark.
 */
@Composable
fun SolidBanner(
    eyebrow: String,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.intake.hero,
    contentColor: Color = MaterialTheme.intake.onHero,
) {
    Surface(
        color = container,
        contentColor = contentColor,
        shape = IntakeShape.Banner,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(IntakeSpacing.CardPadHero)) {
            Eyebrow(eyebrow, color = contentColor.copy(alpha = 0.72f))
            Spacer(Modifier.height(5.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            if (body != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.72f),
                )
            }
        }
    }
}

/** `.li`: icon tile, title over subtitle, chevron. The whole row is the touch target, 56 dp minimum. */
@Composable
fun MenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
    showDivider: Boolean = true,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = IntakeSpacing.ListRowMinHeight)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                Icon(
                    trailing,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Default),
                )
            }
        }
        if (showDivider) ListDivider()
    }
}

/** `.li` of Settings: label over hint on the left, a control on the right, separated by a 1 dp `line`. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    note: (@Composable () -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.ListRowMinHeight).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            control()
        }
        // A long note (a warning, say) goes under the row at full width, never in the control slot.
        if (note != null) Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) { note() }
        if (showDivider) ListDivider()
    }
}

/** A small neutral pill listing an allergen term (`.chip`, display only). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.intake.soft,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * The amber pill that marks a muscle group under its weekly set target. Carries its own words, so it never relies on colour
 * alone.
 */
@Composable
fun Flag(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.intake.warnBg,
        contentColor = MaterialTheme.intake.warn,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

/** The four-up kcal / protein / carb / fat readout under a portion field. Equal columns so the numbers line up as they change. */
@Composable
fun MathGrid(
    entries: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        entries.forEach { (value, caption) ->
            Surface(
                color = MaterialTheme.intake.soft,
                shape = IntakeShape.Tile,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = value,
                        style = NumberSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The "available without a signal" badge: an `ok` chip with a dot. */
@Composable
fun OfflinePill(text: String = "Offline ready", modifier: Modifier = Modifier) {
    val c = MaterialTheme.intake
    Surface(
        color = c.okBg,
        contentColor = c.ok,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(c.ok, CircleShape),
            )
            Eyebrow(text, color = c.ok)
        }
    }
}

/**
 * A `soft` strip for information that is active but not alarming (a running rest timer, a soft allergen match). It is the
 * mockup's `.card.soft`.
 */
@Composable
fun SandStrip(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.intake.soft,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.InnerCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * `.sw`: the switch of the mockup, a 50 x 30 pill: `line` track with a light knob off, `ink` track with a lime knob on.
 * Stock [Switch] in the new theme is already an ink-track switch; this adds the lime knob. Prefer it in new and restyled screens.
 */
@Composable
fun IntakeSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.intake.accent,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** A 22 dp line icon in the muted colour, for rows and fields. */
@Composable
fun MutedIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(IconSize.Default),
    )
}

/** A trailing chevron for list rows. */
@Composable
fun RowChevron(modifier: Modifier = Modifier) = MutedIcon(IntakeIcons.ChevronRight, modifier)
