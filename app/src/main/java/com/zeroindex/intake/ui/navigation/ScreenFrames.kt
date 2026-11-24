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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.IntakeTopBar

/** Room a tab root keeps at its bottom for the dock: 70 dp dock + 20 dp above the inset + 16 dp (DESIGN-V6 a.3 tabRootBottom). */
val PillHeight: Dp = 106.dp

/** Distance from the bottom inset to the top of the dock plus 12 dp: where the live workout bar and the snackbar float. */
val AboveDock: Dp = 102.dp

/** True when the pill is drawn as a vertical rail at the start edge (wide screens): no bottom clearance then. */
val LocalPillIsRail = compositionLocalOf { false }

/** Extra room tab roots leave at the bottom while the minimised session bar floats above the pill (0 when it is not shown). */
val LocalSessionBarSpace = compositionLocalOf { 0.dp }

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
        InsetSides(inner) {
            content(
                PaddingValues(
                    top = inner.calculateTopPadding(),
                    bottom = (if (rail) nav else nav + PillHeight) + LocalSessionBarSpace.current,
                ),
            )
        }
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
    ) { inner ->
        InsetSides(inner) { content(PaddingValues(top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding())) }
    }
}

/**
 * Keeps the content clear of the start and end insets (the navigation bar in landscape with three-button navigation,
 * a cutout). They are applied here, once, for every tab root and pushed screen: a screen that builds its own
 * horizontal padding (16 dp) then sits inside them instead of under the bar (QA Q5a-8).
 */
@Composable
private fun InsetSides(inner: PaddingValues, content: @Composable () -> Unit) {
    val direction = LocalLayoutDirection.current
    Box(
        Modifier.fillMaxSize().padding(
            start = inner.calculateLeftPadding(direction),
            end = inner.calculateRightPadding(direction),
        ),
    ) { content() }
}

/**
 * A full-screen task with its own chrome (cook mode, active workout, the welcome flow): no shell top bar, no pill.
 * The status and navigation bars are still kept clear of the content.
 */
@Composable
fun FullScreenTask(modifier: Modifier = Modifier, content: @Composable (PaddingValues) -> Unit) {
    val direction = LocalLayoutDirection.current
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The start and end insets (the navigation bar in landscape, a cutout) are applied here so the screen's own top bar
        // sits inside them (QA Q7-11); top and bottom are handed to the content.
        Box(Modifier.fillMaxSize().padding(start = bars.calculateLeftPadding(direction), end = bars.calculateRightPadding(direction))) {
            content(WindowInsets.systemBars.asPaddingValues())
        }
    }
}
