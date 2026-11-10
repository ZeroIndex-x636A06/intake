package com.zeroindex.intake.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The one top bar of the app. Tab roots pass [avatarInitial] and [onAvatar] (and no [onBack]); pushed screens
 * pass [onBack] and a [title]. The bar consumes the status bar and cutout insets itself ([windowInsets]), so
 * content below it never needs its own top padding, and nothing is drawn under the clock.
 *
 * [titleContent] replaces the title text (Today puts the date and its arrows there).
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
    )
    val titleSlot: @Composable () -> Unit = {
        if (titleContent != null) {
            titleContent()
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
    val nav: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    }
    val trailing: @Composable RowScope.() -> Unit = {
        if (onSearch != null) {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search") }
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

/** The avatar: an initial on a pale disc, 34 dp inside a 48 dp touch target. Opens You. */
@Composable
fun AvatarButton(initial: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(end = 8.dp)
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "You: profile and settings"
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(initial.take(1).uppercase().ifEmpty { "Y" }, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
