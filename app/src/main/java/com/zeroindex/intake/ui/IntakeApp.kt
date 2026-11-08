package com.zeroindex.intake.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zeroindex.intake.ui.adaptive.LocalWindowSizeClass
import com.zeroindex.intake.ui.adaptive.isAtLeastMedium
import com.zeroindex.intake.ui.adaptive.isExpanded
import com.zeroindex.intake.ui.adaptive.readableWidth
import com.zeroindex.intake.ui.navigation.IntakeNavHost
import com.zeroindex.intake.ui.navigation.Route
import com.zeroindex.intake.ui.navigation.TopLevelDestination

@Composable
fun IntakeApp(messages: AppMessages? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Full-screen flows (cook mode, active workout) hide the navigation: SPEC §9
    // wants cook mode full-screen with oversized targets and no chrome.
    val hideBottomBar = currentDestination?.route?.let { route ->
        route.contains("CookMode") || route.contains("ActiveWorkout")
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

    val navigateTo: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Amendment §3: from Medium width up the destinations move to a rail on the
    // left, which leaves the full height for content on a landscape display.
    val showRail = LocalWindowSizeClass.current.isAtLeastMedium && !hideBottomBar

    Row(Modifier.fillMaxSize()) {
        if (showRail) {
            IntakeNavigationRail(
                currentDestination = currentDestination,
                onSelect = navigateTo,
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            bottomBar = {
                if (!hideBottomBar && !showRail) {
                    Column {
                        // The mockup separates the bar from the content with a
                        // hairline rather than an elevation shadow.
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            TopLevelDestination.entries.forEach { destination ->
                                val selected = currentDestination?.isIn(destination) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { if (!selected) navigateTo(destination) },
                                    icon = {
                                        // The label is always visible, so a separate
                                        // description would just be read twice.
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text(stringResource(destination.labelRes)) },
                                    alwaysShowLabel = true,
                                    // The mockup's active item is a pale green pill
                                    // behind the icon, in the primary family.
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor =
                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor =
                                            MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            // Today, Progress and Plan B lay themselves out across the full
            // width; every other screen is a single column and gets capped.
            val fullWidth = currentDestination?.let { destination ->
                destination.isIn(TopLevelDestination.TODAY) ||
                    destination.isIn(TopLevelDestination.PROGRESS) ||
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
 * The tablet counterpart of the bottom bar (mockup `.rail` / `.navitem`): the
 * same destinations, icons, labels and selected state. Rail items are 56dp
 * tall, above the 48dp touch-target minimum, and the label is always shown so
 * the selected state is never colour-only (SPEC §3.7).
 */
@Composable
private fun IntakeNavigationRail(
    currentDestination: NavDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.isIn(destination) == true
            NavigationRailItem(
                selected = selected,
                onClick = { if (!selected) onSelect(destination) },
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun NavDestination.isIn(destination: TopLevelDestination): Boolean =
    hierarchy.any { it.hasRoute(destination.route::class) }
