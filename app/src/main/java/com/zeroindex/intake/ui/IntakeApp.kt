package com.zeroindex.intake.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.onboarding.GateDecision
import com.zeroindex.intake.domain.onboarding.PendingDestination
import com.zeroindex.intake.domain.onboarding.TourIds
import com.zeroindex.intake.ui.onboarding.AppFlowViewModel
import com.zeroindex.intake.ui.onboarding.LocalTourState
import com.zeroindex.intake.ui.onboarding.TourOverlay
import com.zeroindex.intake.ui.onboarding.TourState
import com.zeroindex.intake.ui.onboarding.tourTarget
import com.zeroindex.intake.ui.navigation.TabGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.zeroindex.intake.ui.navigation.pillBackdropSource
import com.zeroindex.intake.ui.navigation.rememberPillBackdrop
import com.zeroindex.intake.ui.navigation.tabOf
import com.zeroindex.intake.ui.navigation.tabRootOf
import com.zeroindex.intake.ui.navigation.ShellPolicy
import com.zeroindex.intake.ui.navigation.ShellActions
import com.zeroindex.intake.ui.navigation.LocalShellActions
import com.zeroindex.intake.ui.navigation.LocalPillIsRail
import com.zeroindex.intake.ui.navigation.PillHeight
import com.zeroindex.intake.ui.motion.Motion

@Composable
fun IntakeApp(messages: AppMessages? = null) {
    val flow: AppFlowViewModel = hiltViewModel()
    val userReduce by flow.reduceMotionSetting.collectAsStateWithLifecycle()
    // System animator scale 0 OR the in-app switch (MotionRules.reduceMotion).
    val reduce = rememberSystemReduceMotion() || userReduce
    CompositionLocalProvider(LocalReduceMotion provides reduce) {
        val gate by flow.gate.collectAsStateWithLifecycle()
        val decision = gate
        if (decision == null) {
            // A few milliseconds while the welcome-or-tabs decision is made.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        } else {
            IntakeAppContent(messages, flow, startOnboarding = decision == GateDecision.SHOW_ONBOARDING)
        }
    }
}

/** Where each "Your plans" choice opens: its tab, then the screen. */
private fun PendingDestination.target(): Pair<TopLevelDestination, Route> = when (this) {
    PendingDestination.MEAL_PLAN_IMPORT, PendingDestination.MEAL_PLAN_BUILD -> TopLevelDestination.NUTRITION to Route.MealPlans
    PendingDestination.PROGRAM_IMPORT -> TopLevelDestination.TRAIN to Route.Programs
    PendingDestination.PROGRAM_GENERATE -> TopLevelDestination.TRAIN to Route.ProgramGenerator
    PendingDestination.PROGRAM_TEMPLATE -> TopLevelDestination.TRAIN to Route.ProgramTemplates
}

@Composable
private fun IntakeAppContent(messages: AppMessages?, flow: AppFlowViewModel, startOnboarding: Boolean) {
    val navController = rememberNavController()
    val backdrop = rememberPillBackdrop()
    val tour = remember { TourState() }
    val prefs by flow.prefs.collectAsStateWithLifecycle()
    val avatarInitial by flow.avatarInitial.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoot = tabRootOf(currentDestination)
    // The pill is shown on tab roots only (ShellPolicy); a pushed screen, a sheet or a full-screen task hides it.
    val pillVisible = ShellPolicy.pillVisible(currentRoot?.route)
    val reduce = LocalReduceMotion.current

    val snackbar = remember { SnackbarHostState() }
    if (messages != null) {
        val open by messages.openRequest.collectAsState()
        LaunchedEffect(open) {
            if (open == com.zeroindex.intake.work.CoachNotifier.OPEN_COACH) {
                navController.navigate(Route.WeeklyCheckIn)
                messages.openConsumed()
            }
        }
    }
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
    val selectedIndex = currentRoot?.let(destinations::indexOf) ?: tabOf(currentDestination)?.let(destinations::indexOf) ?: -1
    val onSelectTab: (Int) -> Unit = { index ->
        selectTab(
            navController = navController,
            target = destinations[index],
            currentTab = currentRoot,
            atRoot = currentDestination?.hasRoute(destinations[index].route::class) == true,
        )
    }

    // Amendment §3: from Medium width up the destinations move to a vertical pill on the
    // left, which leaves the full height for content on a landscape display.
    val showRail = LocalWindowSizeClass.current.isAtLeastMedium

    // The Today tour starts once, when it is due and Today is showing with its targets laid out.
    val onToday = currentDestination?.hasRoute(Route.Today::class) == true
    DisposableEffect(tour) {
        tour.onFinished = { flow.tourFinished() }
        onDispose { tour.onFinished = null }
    }
    LaunchedEffect(prefs.tourPending, onToday) {
        if (prefs.loaded && prefs.tourPending && prefs.done && onToday && !tour.running) {
            val ready = withTimeoutOrNull(5_000) {
                snapshotFlow { TourIds.RING in tour.available && TourIds.PILL in tour.available }.first { it }
            }
            if (ready == true) {
                delay(500)
                if (!tour.start()) flow.tourFinished()
            }
        }
    }

    // A "Your plans" choice opens once onboarding is done and the tour has finished or was skipped.
    val ready = remember(prefs) { flow.readyDestination() }
    LaunchedEffect(ready) {
        val (tab, route) = ready?.target() ?: return@LaunchedEffect
        flow.pendingOpened()
        navController.navigate(tab.graph) {
            popUpTo<Route.Today> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        navController.navigate(route)
    }

    val shellActions = remember(avatarInitial) {
        ShellActions(avatarInitial) { navController.navigate(ShellPolicy.avatarTarget()) }
    }

    CompositionLocalProvider(
        LocalTourState provides tour,
        LocalShellActions provides shellActions,
        LocalPillIsRail provides showRail,
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxSize()) {
                if (showRail && pillVisible) {
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
                            backdrop = backdrop,
                            modifier = Modifier.tourTarget(TourIds.PILL),
                        )
                    }
                }
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
                    contentPadding = PaddingValues(),
                    modifier = (if (capWidth) Modifier.readableWidth() else Modifier)
                        .weight(1f)
                        .fillMaxHeight()
                        .pillBackdropSource(backdrop),
                    startOnboarding = startOnboarding,
                )
            }
            // The pill floats over the tab roots (they reserve room for it) and slides away on pushed screens.
            if (!showRail) {
                AnimatedVisibility(
                    visible = pillVisible,
                    enter = Motion.pillEnter(reduce),
                    exit = Motion.pillExit(reduce),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
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
                            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().tourTarget(TourIds.PILL),
                            backdrop = backdrop,
                        )
                    }
                }
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = if (pillVisible && !showRail) PillHeight else 0.dp),
            )
            TourOverlay(tour)
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
            popUpTo<Route.Today> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        TabAction.POP_TO_ROOT -> navController.popBackStack(target.route, inclusive = false)
        TabAction.NONE -> Unit
    }
}
