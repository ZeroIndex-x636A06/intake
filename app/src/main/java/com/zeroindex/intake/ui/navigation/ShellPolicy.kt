package com.zeroindex.intake.ui.navigation

/** What the system back button does on a tab root. */
enum class RootBackAction { GO_TO_TODAY, EXIT }

/**
 * The pure rules of the app shell (IA v5 section 2), kept free of Compose and NavController so they can be
 * unit tested: which screens show the pill and the avatar, where the avatar goes, what back does on a root.
 */
object ShellPolicy {
    /** The four tab roots: Today, Nutrition, Train, Progress. */
    fun isTabRoot(route: Route?): Boolean = route != null && TopLevelDestination.entries.any { it.route == route }

    /** The pill is shown on tab roots only. Pushed screens, sheets and full-screen tasks hide it. */
    fun pillVisible(route: Route?): Boolean = isTabRoot(route)

    /** The avatar sits in the top bar of every tab root, and nowhere else. */
    fun avatarVisible(route: Route?): Boolean = isTabRoot(route)

    /** Where the avatar leads, from any tab root. */
    fun avatarTarget(): Route = Route.You

    /** The mockup shows no search icon on any tab root yet ("Find anything" comes later). */
    @Suppress("UNUSED_PARAMETER")
    fun searchVisible(route: Route?): Boolean = false

    /** IA 2a: back on a tab root other than Today goes to Today; on Today it leaves the app. */
    fun backFromRoot(tab: TopLevelDestination): RootBackAction =
        if (tab == TopLevelDestination.TODAY) RootBackAction.EXIT else RootBackAction.GO_TO_TODAY

    /** Tab roots reserve room for the pill (pill height + gap + gesture inset); pushed screens do not. */
    fun reservesPillSpace(route: Route?): Boolean = pillVisible(route)
}
