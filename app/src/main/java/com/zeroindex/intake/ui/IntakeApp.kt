package com.zeroindex.intake.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.zeroindex.intake.ui.adaptive.LocalWindowSizeClass
import com.zeroindex.intake.ui.adaptive.isAtLeastMedium
import com.zeroindex.intake.ui.adaptive.isExpanded
import com.zeroindex.intake.ui.adaptive.readableWidth
import com.zeroindex.intake.ui.motion.LocalReduceMotion
import com.zeroindex.intake.ui.motion.rememberSystemReduceMotion
import com.zeroindex.intake.ui.navigation.FloatingPillBar
import com.zeroindex.intake.ui.navigation.IntakeNavHost
import com.zeroindex.intake.ui.navigation.Route
import com.zeroindex.intake.ui.navigation.TabAction
import com.zeroindex.intake.ui.navigation.TabPolicy
import com.zeroindex.intake.ui.navigation.TopLevelDestination
import com.zeroindex.intake.ui.navigation.tabOf

@Composable
fun IntakeApp(messages: AppMessages? = null) {
    CompositionLocalProvider(LocalReduceMotion provides rememberSystemReduceMotion()) {
        IntakeAppContent(messages)
    }
}

@Composable
private fun IntakeAppContent(messages: AppMessages?) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = tabOf(currentDestination)

    // Full-screen flows (cook mode, active workout) hide the navigation: SPEC §9
    // wants cook mode full-screen with oversized targets and no chrome.
    val hideNavigation = currentDestination?.hierarchy?.any {
        it.hasRoute(Route.CookMode::class) || it.hasRoute(Route.ActiveWorkout::class)
    } == true

    val snackbar = remember { SnackbarHostState() }
    if (messages != null) {
        LaunchedEffect(messages) {
            messages.events.collect { message ->
                messages.consumed()
                val result = snackbar.showSnackbar(
                    message = message.text,
                    actionLabel = message.actionLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
            }
        }
    }

    val destinations = TopLevelDestination.entries
    val selectedIndex = currentTab?.let(destinations::indexOf) ?: -1
    val onSelectTab: (Int) -> Unit = { index ->
        selectTab(
            navController = navController,
            target = destinations[index],
            currentTab = currentTab,
            atRoot = currentDestination?.hasRoute(destinations[index].route::class) == true,
        )
    }

    // Amendment §3: from Medium width up the destinations move to a vertical pill on the
    // left, which leaves the full height for content on a landscape display.
    val showRail = LocalWindowSizeClass.current.isAtLeastMedium && !hideNavigation

    Row(Modifier.fillMaxSize()) {
        if (showRail) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                FloatingPillBar(
                    destinations = destinations,
                    selectedIndex = selectedIndex,
                    onSelect = onSelectTab,
                    vertical = true,
                )
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            snackbarHost = { SnackbarHost(snackbar) },
            // The rail already consumes the start-side system insets, so the
            // content must not pad for them a second time.
            contentWindowInsets = if (showRail) {
                ScaffoldDefaults.contentWindowInsets.only(
                    WindowInsetsSides.End + WindowInsetsSides.Vertical,
                )
            } else {
                ScaffoldDefaults.contentWindowInsets
            },
            // The pill floats over the content. Scaffold measures it and reports its height (plus
            // the gesture-bar inset and margins) as the bottom of `innerPadding`, which is the
            // contentPadding every screen already honours, so content scrolls behind the pill and
            // its last row can still be scrolled clear of it.
            bottomBar = {
                if (!hideNavigation && !showRail) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                            )
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FloatingPillBar(
                            destinations = destinations,
                            selectedIndex = selectedIndex,
                            onSelect = onSelectTab,
                            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                        )
                    }
                }
            },
        ) { innerPadding ->
            // Today, Progress and Plan B lay themselves out across the full
            // width; every other screen is a single column and gets capped.
            val fullWidth = currentDestination?.let { destination ->
                destination.hasRoute(Route.Today::class) ||
                    destination.hasRoute(Route.Progress::class) ||
                    destination.hierarchy.any { it.hasRoute(Route.PlanB::class) }
            } == true
            val capWidth = LocalWindowSizeClass.current.isExpanded && !fullWidth
            IntakeNavHost(
                navController = navController,
                contentPadding = innerPadding,
                modifier = if (capWidth) Modifier.readableWidth() else Modifier,
            )
        }
    }
}

/**
 * Another tab: save the current tab's stack and restore the target's (its own back stack).
 * The current tab again: pop to its root. See [TabPolicy].
 */
private fun selectTab(
    navController: NavHostController,
    target: TopLevelDestination,
    currentTab: TopLevelDestination?,
    atRoot: Boolean,
) {
    when (TabPolicy.actionFor(currentTab, target, atRoot)) {
        TabAction.SWITCH -> navController.navigate(target.graph) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        TabAction.POP_TO_ROOT -> navController.popBackStack(target.route, inclusive = false)
        TabAction.NONE -> Unit
    }
}
