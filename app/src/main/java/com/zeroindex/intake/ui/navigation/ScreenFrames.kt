package com.zeroindex.intake.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.IntakeTopBar

/** Height the floating pill takes at the bottom of a tab root: the pill (72 dp), its gap and its top margin. */
val PillHeight: Dp = 92.dp

/** True when the pill is drawn as a vertical rail at the start edge (wide screens): no bottom clearance then. */
val LocalPillIsRail = compositionLocalOf { false }

/** The avatar initial and what tapping it does, provided once by the app shell. */
class ShellActions(val avatarInitial: String, val onAvatar: () -> Unit)

val LocalShellActions = compositionLocalOf { ShellActions("Y") {} }

/**
 * A tab root: a top bar with the title and the avatar (and no back arrow), content that clears the pill.
 * System back on a root other than Today goes to Today ([ShellPolicy.backFromRoot]).
 */
@Composable
fun TabRootScreen(
    tab: TopLevelDestination,
    title: String,
    onGoToToday: () -> Unit,
    modifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit)? = null,
    centerTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val shell = LocalShellActions.current
    val rail = LocalPillIsRail.current
    BackHandler(enabled = ShellPolicy.backFromRoot(tab) == RootBackAction.GO_TO_TODAY) { onGoToToday() }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            IntakeTopBar(
                title = title,
                avatarInitial = shell.avatarInitial,
                onAvatar = shell.onAvatar,
                titleContent = titleContent,
                centerTitle = centerTitle,
                actions = actions,
            )
        },
    ) { inner ->
        val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        content(
            PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = if (rail) nav else nav + PillHeight,
                start = inner.calculateLeftPadding(LocalLayoutDirection.current),
                end = inner.calculateRightPadding(LocalLayoutDirection.current),
            ),
        )
    }
}

/**
 * A pushed screen: a top bar with a back arrow and the title, no pill. Content gets the top bar's height at the
 * top and the navigation bar (and the keyboard, while a field is focused) at the bottom.
 */
@Composable
fun PushedScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = { IntakeTopBar(title = title, onBack = onBack, actions = actions) },
    ) { inner -> content(inner) }
}

/**
 * A full-screen task with its own chrome (cook mode, active workout, the welcome flow): no shell top bar, no pill.
 * The status and navigation bars are still kept clear of the content.
 */
@Composable
fun FullScreenTask(modifier: Modifier = Modifier, content: @Composable (PaddingValues) -> Unit) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content(WindowInsets.systemBars.asPaddingValues())
    }
}
