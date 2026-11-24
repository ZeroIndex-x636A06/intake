package com.zeroindex.intake.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.tappableElement
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
import com.zeroindex.intake.ui.navigation.IntakeNavHost
import com.zeroindex.intake.ui.navigation.Route
import com.zeroindex.intake.ui.navigation.TabAction
import com.zeroindex.intake.ui.navigation.TabPolicy
import com.zeroindex.intake.ui.navigation.TopLevelDestination
import com.zeroindex.intake.ui.navigation.tabOf
import com.zeroindex.intake.ui.navigation.tabRootOf
import com.zeroindex.intake.ui.navigation.ShellPolicy
import com.zeroindex.intake.ui.navigation.ShellActions
import com.zeroindex.intake.ui.navigation.LocalShellActions
import com.zeroindex.intake.ui.navigation.LocalPillIsRail
import com.zeroindex.intake.ui.navigation.AboveDock
import com.zeroindex.intake.ui.navigation.DockBar
import com.zeroindex.intake.ui.navigation.DockTab
import com.zeroindex.intake.ui.quicklog.QuickLogSheet
import com.zeroindex.intake.ui.quicklog.QuickTile
import com.zeroindex.intake.ui.theme.IntakeSpacing
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.zeroindex.intake.R
import com.zeroindex.intake.ui.navigation.LocalSessionBarSpace
import com.zeroindex.intake.ui.screens.workout.SessionBar
import com.zeroindex.intake.ui.screens.workout.SessionBarHeight
import com.zeroindex.intake.ui.screens.workout.SessionBarViewModel
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
                val result = snackbar.showSnackbar(
                    message = message.text,
                    actionLabel = message.actionLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) messages.perform(message)
            }
        }
    }

    val destinations = TopLevelDestination.entries
    val selectedIndex = currentRoot?.let(destinations::indexOf) ?: tabOf(currentDestination)?.let(destinations::indexOf) ?: -1
    var quickLogOpen by rememberSaveable { mutableStateOf(false) }
    // The sheet only exists over a tab root; leaving the roots (a pushed screen) closes it.
    if (!pillVisible && quickLogOpen) quickLogOpen = false
    val onSelectTab: (Int) -> Unit = { index ->
        quickLogOpen = false
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

    // The minimised workout: a bar above the pill on every tab root while a session is running.
    val sessionBarModel: SessionBarViewModel = hiltViewModel()
    val runningSession by sessionBarModel.running.collectAsStateWithLifecycle()
    val sessionBarShown = pillVisible && runningSession != null

    // Each Quick log tile opens an existing flow. Food tiles open Log food on the matching source.
    val onQuickTile: (QuickTile) -> Unit = { tile ->
        quickLogOpen = false
        val logFood = { mode: String -> navController.navigate(Route.LogFood(mode = mode)) }
        // A tab-graph screen is opened from its own tab, so back lands on that tab's root.
        val inTab = { tab: TopLevelDestination, route: Route ->
            navController.navigate(tab.graph) {
                popUpTo<Route.Today> { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            navController.navigate(route)
        }
        when (tile) {
            QuickTile.FOOD -> logFood("SEARCH")
            QuickTile.DRINK -> navController.navigate(Route.Drinks)
            QuickTile.SCAN -> logFood("BARCODE")
            QuickTile.PHOTO -> logFood("PHOTO")
            QuickTile.QUICK_CALORIES -> logFood("QUICK_ADD")
            QuickTile.MEAL_TEMPLATE, QuickTile.NFC_TAG -> inTab(TopLevelDestination.NUTRITION, Route.MealTemplates)
            QuickTile.WEIGHT, QuickTile.MEASUREMENTS -> navController.navigate(Route.BodyMetrics)
            QuickTile.PROGRESS_PHOTO -> navController.navigate(Route.ProgressPhotos)
            QuickTile.START_WORKOUT -> {
                val running = runningSession
                if (running != null) navController.navigate(Route.ActiveWorkout(running.id))
                else selectTab(navController, TopLevelDestination.TRAIN, currentRoot, atRoot = false)
            }
            QuickTile.JUMP_TEST -> navController.navigate(Route.Performance)
        }
    }
    val compactDock = LocalConfiguration.current.screenHeightDp < 480
    val dockTabs = destinations.map { DockTab(stringResource(it.labelRes), it.icon) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val quickLogLabel = stringResource(if (quickLogOpen) R.string.nav_quick_log_close else R.string.nav_quick_log)

    CompositionLocalProvider(
        LocalTourState provides tour,
        LocalShellActions provides shellActions,
        LocalPillIsRail provides showRail,
        LocalSessionBarSpace provides if (sessionBarShown) SessionBarHeight else 0.dp,
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
                        DockBar(
                            tabs = dockTabs,
                            selectedIndex = selectedIndex,
                            onSelect = onSelectTab,
                            onFab = { quickLogOpen = !quickLogOpen },
                            fabLabel = quickLogLabel,
                            fabOpen = quickLogOpen,
                            vertical = true,
                            modifier = Modifier.tourTarget(TourIds.PILL),
                        )
                    }
                }
                // Today and Progress lay themselves out across the full
                // width; every other screen is a single column and gets capped.
                val fullWidth = currentDestination?.let { destination ->
                    destination.hasRoute(Route.Today::class) ||
                        destination.hasRoute(Route.Progress::class)
                } == true
                val capWidth = LocalWindowSizeClass.current.isExpanded && !fullWidth
                IntakeNavHost(
                    navController = navController,
                    contentPadding = PaddingValues(),
                    modifier = (if (capWidth) Modifier.readableWidth() else Modifier)
                        .weight(1f)
                        .fillMaxHeight(),
                    startOnboarding = startOnboarding,
                )
            }
            NavigationBarScrim()
            // The Quick log sheet sits under the dock (the dock stays visible above the scrim) and over the screen.
            if (pillVisible) {
                QuickLogSheet(
                    visible = quickLogOpen,
                    onDismiss = { quickLogOpen = false },
                    onTile = onQuickTile,
                    dockClearance = if (showRail) 0.dp else navBottom + IntakeSpacing.DockBottom + IntakeSpacing.DockHeight,
                )
            }
            // The dock floats over the tab roots (they reserve room for it) and slides away on pushed screens.
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
                            .padding(start = IntakeSpacing.DockInset, end = IntakeSpacing.DockInset, bottom = IntakeSpacing.DockBottom),
                        contentAlignment = Alignment.Center,
                    ) {
                        DockBar(
                            tabs = dockTabs,
                            selectedIndex = selectedIndex,
                            onSelect = onSelectTab,
                            onFab = { quickLogOpen = !quickLogOpen },
                            fabLabel = quickLogLabel,
                            fabOpen = quickLogOpen,
                            compact = compactDock,
                            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().tourTarget(TourIds.PILL),
                        )
                    }
                }
            }
            runningSession?.let { running ->
                AnimatedVisibility(
                    visible = sessionBarShown,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                            )
                            .padding(start = 16.dp, end = 16.dp, bottom = if (showRail) 12.dp else AboveDock),
                        contentAlignment = Alignment.Center,
                    ) {
                        SessionBar(
                            session = running,
                            onResume = { navController.navigate(Route.ActiveWorkout(running.id)) },
                            modifier = Modifier.widthIn(max = 560.dp),
                        )
                    }
                }
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = (if (pillVisible && !showRail) AboveDock else 0.dp) + (if (sessionBarShown) SessionBarHeight else 0.dp)),
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

/**
 * With three-button navigation the bar is drawn over the app (edge to edge), so scrolled cards show through behind its icons
 * (QA Q7-21). Paint the bar's area in the app background. The tappable-element inset is zero with gesture navigation, so the
 * floating dock and the gesture pill are left alone. It draws only; it takes no touches.
 */
@Composable
private fun NavigationBarScrim() {
    val tappable = WindowInsets.tappableElement
    val color = MaterialTheme.colorScheme.background
    val direction = LocalLayoutDirection.current
    Box(
        Modifier.fillMaxSize().drawBehind {
            val bottom = tappable.getBottom(this).toFloat()
            val left = tappable.getLeft(this, direction).toFloat()
            val right = tappable.getRight(this, direction).toFloat()
            if (bottom > 0f) drawRect(color, topLeft = Offset(0f, size.height - bottom), size = Size(size.width, bottom))
            if (left > 0f) drawRect(color, topLeft = Offset.Zero, size = Size(left, size.height))
            if (right > 0f) drawRect(color, topLeft = Offset(size.width - right, 0f), size = Size(right, size.height))
        },
    )
}
