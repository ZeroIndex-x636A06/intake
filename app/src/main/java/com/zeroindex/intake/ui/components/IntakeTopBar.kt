package com.zeroindex.intake.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing

/**
 * The one top bar of the app. Tab roots pass [avatarInitial] and [onAvatar] (and no [onBack]): the title is the 30 sp screen
 * title (`.title`) with the lime avatar on the right. Pushed screens pass [onBack] and a [title]: a 44 dp circular back button
 * on the left and a 19 sp title (`.h2`). The bar consumes the status bar and cutout insets itself ([windowInsets]), so content
 * below it never needs its own top padding, and nothing is drawn under the clock.
 *
 * [titleContent] replaces the title text (Today puts the date there).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    avatarInitial: String? = null,
    onAvatar: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    centerTitle: Boolean = false,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
        actionIconContentColor = MaterialTheme.colorScheme.onBackground,
    )
    val isRoot = onBack == null
    val titleSlot: @Composable () -> Unit = {
        if (titleContent != null) {
            titleContent()
        } else {
            Text(
                text = title,
                style = if (isRoot && !centerTitle) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
    val nav: @Composable () -> Unit = {
        if (onBack != null) {
            FilledIconButton(
                onClick = onBack,
                modifier = Modifier.size(IntakeSpacing.TouchTarget),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(IntakeIcons.ChevronLeft, contentDescription = "Back", modifier = Modifier.size(IconSize.Default))
            }
        }
    }
    val trailing: @Composable RowScope.() -> Unit = {
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(IntakeIcons.Search, contentDescription = "Search", modifier = Modifier.size(IconSize.Default))
            }
        }
        actions()
        if (avatarInitial != null && onAvatar != null) {
            AvatarButton(initial = avatarInitial, onClick = onAvatar)
        }
    }
    if (centerTitle) {
        CenterAlignedTopAppBar(
            title = titleSlot, modifier = modifier, navigationIcon = nav, actions = trailing,
            colors = colors, windowInsets = windowInsets,
        )
    } else {
        TopAppBar(
            title = titleSlot, modifier = modifier, navigationIcon = nav, actions = trailing,
            colors = colors, windowInsets = windowInsets,
        )
    }
}

/** The avatar (`.avatar`): the user's initial on a 44 dp lime disc, the 44 dp touch target itself. Opens You. */
@Composable
fun AvatarButton(initial: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(end = 8.dp)
            // Clip before the click so the press ripple is round, like the avatar.
            .clip(IntakeShape.Pill)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "You: profile and settings"
            },
        contentAlignment = Alignment.Center,
    ) {
        AvatarDisc(initial)
    }
}
