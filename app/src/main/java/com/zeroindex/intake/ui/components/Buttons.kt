package com.zeroindex.intake.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.SpaceGrotesk
import com.zeroindex.intake.ui.theme.intake

/**
 * The button family of DESIGN-V6 b.3. A plain Material `Button` is already the primary one (an ink pill); these are the other
 * four, all 44 dp tall pills. Disabled is the Material default (38% alpha). Use at most one [AccentButton] per screen.
 */
private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
private val SmallPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)

@Composable
private fun buttonColors(container: Color, content: Color): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = container,
    contentColor = content,
    disabledContainerColor = container.copy(alpha = 0.38f),
    disabledContentColor = content.copy(alpha = 0.38f),
)

/** `.btn.accent`: lime fill, `onAccent` text. The hero "Log food", Start. */
@Composable
fun AccentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = IntakeSpacing.TouchTarget),
        enabled = enabled,
        shape = IntakeShape.Button,
        colors = buttonColors(MaterialTheme.intake.accent, MaterialTheme.intake.onAccent),
        contentPadding = if (small) SmallPadding else ButtonPadding,
        content = content,
    )
}

/** `.btn`: `soft` fill, ink text. A secondary action ("Swap", "Open plan"). */
@Composable
fun SoftButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = IntakeSpacing.TouchTarget),
        enabled = enabled,
        shape = IntakeShape.Button,
        colors = buttonColors(MaterialTheme.intake.soft, MaterialTheme.colorScheme.onSurface),
        contentPadding = if (small) SmallPadding else ButtonPadding,
        content = content,
    )
}

/** `.btn.danger`: `badBg` fill, `bad` text. "Log it anyway", Discard: destructive but allowed, never an ink primary. */
@Composable
fun DangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = IntakeSpacing.TouchTarget),
        enabled = enabled,
        shape = IntakeShape.Button,
        colors = buttonColors(MaterialTheme.intake.badBg, MaterialTheme.intake.bad),
        contentPadding = if (small) SmallPadding else ButtonPadding,
        content = content,
    )
}

/** `.btn.line`: transparent with a 1.5 dp `line` outline, ink text ("Add exercise"). */
@Composable
fun LineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = IntakeSpacing.TouchTarget),
        enabled = enabled,
        shape = IntakeShape.Button,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = if (small) SmallPadding else ButtonPadding,
        content = content,
    )
}

/** `.btn.ghost`: text only ("More detail", "Dismiss"). */
@Composable
fun GhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = IntakeSpacing.TouchTarget),
        enabled = enabled,
        shape = IntakeShape.Button,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 10.dp),
        content = content,
    )
}

/** Where an [IconCircleButton] sits: on the ground (`card` fill), inside a card (`soft`), or bare. */
enum class IconCircleStyle { Card, Soft, Clear }

/**
 * `.iconbtn`: a 44 dp circle holding a 22 dp line icon. [contentDescription] is required: an icon button has no other label.
 */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconCircleStyle = IconCircleStyle.Card,
    enabled: Boolean = true,
) {
    val container = when (style) {
        IconCircleStyle.Card -> MaterialTheme.colorScheme.surface
        IconCircleStyle.Soft -> MaterialTheme.intake.soft
        IconCircleStyle.Clear -> Color.Transparent
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(IntakeSpacing.TouchTarget),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = container,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = container.copy(alpha = container.alpha * 0.4f),
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(IconSize.Default))
    }
}

/** `.avatar`: a lime disc with the user's initial in Space Grotesk. 44 dp, 64 dp (and 26 sp) on You. */
@Composable
fun AvatarDisc(initial: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Surface(
        shape = IntakeShape.Pill,
        color = MaterialTheme.intake.accent,
        contentColor = MaterialTheme.intake.onAccent,
        modifier = modifier.size(if (large) 64.dp else IntakeSpacing.TouchTarget),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial.take(1).uppercase().ifEmpty { "Y" },
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (large) 26.sp else 17.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
