package com.zeroindex.intake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.zeroindex.intake.ui.motion.SkeletonLine
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.NumberSmall
import com.zeroindex.intake.ui.theme.intake

/**
 * Status pills, the designed states (empty, loading, error), labelled form fields, the per-permission switch pill and the
 * equation strip, in the one status language of DESIGN-V6: green Fits, grey neutral, amber caution, red blocked, violet AI.
 */

enum class PillTone { Ok, Wait, Bad }

/** A status pill: `ok` (green), `wait` (amber) or `bad` (red). Its words carry the meaning. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.Ok) {
    val c = MaterialTheme.intake
    val (bg, fg) = when (tone) {
        PillTone.Ok -> c.okBg to c.ok
        PillTone.Wait -> c.warnBg to c.warn
        PillTone.Bad -> c.badBg to c.bad
    }
    Surface(color = bg, contentColor = fg, shape = CircleShape, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
        )
    }
}

/** The amber chip naming where data goes or what a model keeps, e.g. "Sends goals to OpenRouter". */
@Composable
fun PrivacyTag(text: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.intake
    Surface(
        color = c.warnBg,
        contentColor = c.warn,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/**
 * An empty state: a centred card, quiet. [title] is the row title when present; [icon] is the optional 22 dp line icon on a
 * 40 dp `soft` tile above it.
 */
@Composable
fun EmptyState(
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = IntakeShape.Card,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(IntakeSpacing.CardPad), horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                IconTile(icon, Modifier.padding(bottom = 10.dp))
            }
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(10.dp))
                action()
            }
        }
    }
}

/** A spinner-free loading state: one line per step, over skeleton blocks on a `soft` card. */
@Composable
fun LoadingState(steps: List<String>, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.intake.soft,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = IntakeShape.Card,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(IntakeSpacing.CardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(steps.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            // A shimmering placeholder says "something is on its way" without a spinner.
            SkeletonLine(fraction = 0.9f, height = 10.dp)
            SkeletonLine(fraction = 0.6f, height = 10.dp)
        }
    }
}

/** An error state: `badBg` fill with `bad` text, a sentence on what still works, optional retry. */
@Composable
fun ErrorState(
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.intake
    Surface(
        color = c.badBg,
        contentColor = c.bad,
        shape = IntakeShape.Card,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(IntakeSpacing.CardPad)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (action != null) action()
        }
    }
}

/** A `soft` note that explains a rule rather than reports a state. */
@Composable
fun SandNote(body: String, modifier: Modifier = Modifier, title: String? = null) {
    Surface(
        color = MaterialTheme.intake.soft,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.InnerCard,
        modifier = modifier.fillMaxWidth().padding(top = 9.dp),
    ) {
        Column(Modifier.padding(IntakeSpacing.CardPadCompact + 2.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * `.field`: label above (12.5 sp, 600, muted), 48 dp box below with a 14 dp radius. The label is a separate line, not a
 * floating hint, so it stays readable once the field is filled. Focus turns the border ink, an error turns it red.
 */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    secret: Boolean = false,
    isError: Boolean = false,
    supporting: String? = null,
) {
    Column(modifier.scrollIntoViewOnFocus()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = true,
            isError = isError,
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            shape = IntakeShape.Input,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                errorBorderColor = MaterialTheme.intake.bad,
                errorSupportingTextColor = MaterialTheme.intake.bad,
            ),
            supportingText = supporting?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
    }
}

/** `.form-grid`: two equal columns. Pass `null` for an empty right cell. */
@Composable
fun FormRow(
    modifier: Modifier = Modifier,
    left: @Composable (Modifier) -> Unit,
    right: (@Composable (Modifier) -> Unit)?,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        left(Modifier.weight(1f))
        if (right != null) right(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

/**
 * One permission or toggle per row with its own words ("Allowed" / "Not allowed"), so state never depends on colour: an `ok`
 * chip when on, a `soft` one when off.
 */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onLabel: String = "Allowed",
    offLabel: String = "Not allowed",
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = IntakeSpacing.ListRowMinHeight).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val c = MaterialTheme.intake
        Surface(
            onClick = onToggle,
            enabled = enabled,
            color = if (on) c.okBg else c.soft,
            contentColor = if (on) c.ok else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
            modifier = Modifier
                .heightIn(min = IntakeSpacing.TouchTarget)
                .semantics {
                    role = Role.Switch
                    stateDescription = if (on) onLabel else offLabel
                    contentDescription = title
                },
        ) {
            Box(Modifier.heightIn(min = IntakeSpacing.TouchTarget).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(text = if (on) onLabel else offLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** `.equation`: three tiles joined by operators, e.g. raw burn - BMR = added. */
@Composable
fun EquationStrip(
    terms: List<Pair<String, String>>,
    operators: List<String>,
    modifier: Modifier = Modifier,
) {
    require(operators.size == terms.size - 1) { "one operator between each pair of terms" }
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        terms.forEachIndexed { i, (value, caption) ->
            Surface(
                color = MaterialTheme.intake.soft,
                shape = IntakeShape.Tile,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value, style = NumberSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (i < operators.size) {
                Text(
                    operators[i],
                    style = NumberSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The small `soft` square in front of an NFC tag or device row. */
@Composable
fun TagIcon(glyph: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(
        color = MaterialTheme.intake.soft,
        contentColor = tint,
        shape = IntakeShape.IconSmall,
        modifier = modifier.size(28.dp),
    ) {
        Text(glyph, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * A filter the user cannot switch off here, e.g. an allergen from their profile. Not clickable, and says so in its own words.
 */
@Composable
fun LockChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.intake.soft,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.Chip,
        modifier = modifier.heightIn(min = 40.dp),
    ) {
        Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "🔒 $text",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * A result card (recipe, restaurant): `surface` on the ground, 20 dp corners, no border. [dimmed] is the rejected variant, for
 * results kept on screen only to say why they were removed.
 */
@Composable
fun ResultCard(
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        color = if (dimmed) MaterialTheme.intake.soft else MaterialTheme.colorScheme.surface,
        shape = IntakeShape.TileLarge,
        modifier = modifier.fillMaxWidth().then(if (dimmed) Modifier.alpha(0.56f) else Modifier),
    ) {
        Column(Modifier.padding(IntakeSpacing.CardPadCompact + 2.dp)) { content() }
    }
}

/** `.macro` tiles: three `soft` tiles, protein / carbs / kcal by default, the value in the macro colour. */
@Composable
fun MacroTiles(tiles: List<Triple<String, String, Color>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { (value, caption, tint) ->
            Surface(color = MaterialTheme.intake.soft, shape = IntakeShape.InnerCard, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // The value stays ink: the pale macro colours (carbs orange on `soft`) do not reach 4.5:1 as text. The
                    // colour is carried by the dot, and the word beside it says which macro it is.
                    Text(value, style = NumberSmall, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(8.dp).background(tint, CircleShape))
                        Text(
                            caption.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** A 14 dp icon inside text-height rows; here so a row can pair an [IntakeChip] icon without importing the size. */
@Composable
fun SmallIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(IconSize.Small))
}
