package com.zeroindex.intake.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Primitives introduced by design/intake-mockup-v2.html: status pills, the
 * three designed states (empty, loading, error), labelled form fields, the
 * per-permission switch pill and the equation strip.
 */

enum class PillTone { Ok, Wait, Bad }

/** `.status-pill` (pale), `.status-pill.wait` (sand), `.status-pill.bad` (clay). */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.Ok) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        PillTone.Ok -> scheme.primaryContainer to scheme.onPrimaryContainer
        PillTone.Wait -> scheme.secondaryContainer to scheme.onSecondaryContainer
        PillTone.Bad -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Surface(color = bg, contentColor = fg, shape = CircleShape, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** `.privacy-tag`: the clay chip naming where data goes, e.g. "Sends photo to OpenRouter". */
@Composable
fun PrivacyTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

/** `.empty-state`: dashed, centred, quiet. [title] is bold when present. */
@Composable
fun EmptyState(
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth().padding(vertical = 9.dp),
    ) {
        Column(Modifier.padding(15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}

/** `.loading-state`: a spinner and one line per step, so slow work says what it is doing. */
@Composable
fun LoadingState(steps: List<String>, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(17.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 9.dp),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp).padding(top = 1.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(steps.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** `.error-state`: clay fill, bold title, a sentence on what still works, optional retry. */
@Composable
fun ErrorState(
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(17.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 9.dp),
    ) {
        Column(Modifier.padding(15.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall)
            if (action != null) action()
        }
    }
}

/** `.conflict` and other sand notes that explain a rule rather than report a state. */
@Composable
fun SandNote(body: String, modifier: Modifier = Modifier, title: String? = null) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(15.dp),
        modifier = modifier.fillMaxWidth().padding(top = 9.dp),
    ) {
        Column(Modifier.padding(11.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * `.field`: label above, 46dp box below. The label is a separate line, not a
 * floating hint, so it stays readable once the field is filled.
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
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 5.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = true,
            isError = isError,
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
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
 * `.health-row` + `.switch`: one permission or toggle per row with its own
 * words ("Allowed" / "Not allowed"), so state never depends on colour.
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
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val scheme = MaterialTheme.colorScheme
        Surface(
            onClick = onToggle,
            enabled = enabled,
            color = if (on) scheme.primaryContainer else scheme.surfaceVariant,
            contentColor = if (on) scheme.primary else scheme.onSurfaceVariant,
            border = BorderStroke(1.dp, if (on) scheme.primary else scheme.outlineVariant),
            shape = CircleShape,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics {
                    role = Role.Switch
                    stateDescription = if (on) onLabel else offLabel
                    contentDescription = title
                },
        ) {
            Text(
                text = if (on) onLabel else offLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            )
        }
    }
}

/** `.equation`: three tiles joined by operators, e.g. raw burn − BMR = added. */
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
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
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
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** `.tag-icon`: the small sand square in front of an NFC tag or device row. */
@Composable
fun TagIcon(glyph: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = tint,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.size(28.dp),
    ) {
        Text(glyph, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}
