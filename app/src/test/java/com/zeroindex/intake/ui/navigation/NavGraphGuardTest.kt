package com.zeroindex.intake.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * A pure JVM guard on the navigation graph, by scanning the sources the way `AiAuditTest` does.
 *
 * Shape (IA v5, Option A): four tab graphs (Today, Nutrition, Train, Progress), each for screens only that tab opens, and
 * one block of **shared screens registered once at the root**. It catches `navigate(Route.X)` with no
 * `composable<Route.X>` behind it, a screen registered in two places, and a navigation from a tab graph or from a root
 * screen to a route it cannot reach (a sibling tab graph's screen, which Navigation does not find).
 */
class NavGraphGuardTest {

    private val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
    private val navDir = sourceRoot.resolve("com/zeroindex/intake/ui/navigation")
    private val host = navDir.resolve("IntakeNavHost.kt").readText()
    private val destinations = navDir.resolve("Destinations.kt").readText()

    /** Routes outside every graph on purpose (the welcome flow). */
    private val outsideTabs = setOf("Onboarding")

    private val registerRx = Regex("""(?:composable|dialog)<Route\.(\w+)>""")
    private val navigateRx = Regex("""navigate\(\s*Route\.(\w+)""")
    private val tabRoots = setOf("Today", "Nutrition", "Train", "Progress")

    private fun block(text: String, openBrace: Int): String {
        var depth = 1
        var i = openBrace + 1
        while (depth > 0 && i < text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return text.substring(openBrace + 1, i - 1)
    }

    private class Graph(val name: String, val body: String) {
        val registered: List<String> = Regex("""(?:composable|dialog)<Route\.(\w+)>""").findAll(body).map { it.groupValues[1] }.toList()
        val navigated: Set<String> = Regex("""navigate\(\s*Route\.(\w+)""").findAll(body).map { it.groupValues[1] }.toSet()
    }

    private val graphRanges: List<Triple<String, Int, Int>> by lazy {
        Regex("""navigation<TabGraph\.(\w+)>\(startDestination = Route\.\w+\) \{""").findAll(host).map { m ->
            val open = m.range.last
            Triple(m.groupValues[1], m.range.first, open + block(host, open).length + 2)
        }.toList()
    }

    private val graphs: List<Graph> by lazy { graphRanges.map { (name, start, end) -> Graph(name, host.substring(start, end)) } }

    /** The host text with every tab graph cut out: what is registered at the NavHost root. */
    private val rootBody: String by lazy {
        val sb = StringBuilder(host)
        graphRanges.sortedByDescending { it.second }.forEach { (_, start, end) -> sb.delete(start, end) }
        sb.toString()
    }
    private val rootRegistered: List<String> by lazy { registerRx.findAll(rootBody).map { it.groupValues[1] }.toList() }
    private val rootNavigated: Set<String> by lazy { navigateRx.findAll(rootBody).map { it.groupValues[1] }.toSet() }

    private val routes: Set<String> by lazy {
        Regex("""@Serializable data (?:object|class) (\w+)\(?[^\n]*: Route""").findAll(destinations).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `the scan sees the four tab graphs and the routes`() {
        assertThat(graphs.map { it.name }).containsExactly("Today", "Nutrition", "Train", "Progress").inOrder()
        assertThat(routes).containsAtLeast("You", "Settings", "SettingsGroup", "Allergens", "Drinks", "SwapSheet", "WeeklyCheckIn", "ProgressPhotos")
        assertThat(routes).containsNoneOf("More", "Food", "Backup", "AiSettings", "HealthConnect", "FoodData", "MyAllergens", "AllergenReference", "WeeklyReport", "CoachCheckIn", "Help")
    }

    @Test
    fun `every route has a composable`() {
        val registered = registerRx.findAll(host).map { it.groupValues[1] }.toSet()
        assertWithMessage("Routes with no composable<...> in IntakeNavHost").that(routes - registered).isEmpty()
    }

    @Test
    fun `every navigate in a tab graph targets a route in that graph or at the root`() {
        val problems = graphs.flatMap { g ->
            (g.navigated - g.registered.toSet() - rootRegistered.toSet() - outsideTabs).map { "${g.name} graph navigates to Route.$it but cannot reach it" }
        }
        assertWithMessage(problems.joinToString("\n")).that(problems).isEmpty()
    }

    @Test
    fun `a root screen only navigates to root routes`() {
        val bad = rootNavigated - rootRegistered.toSet() - outsideTabs
        assertWithMessage("Root screens navigate to routes inside a tab graph: $bad").that(bad).isEmpty()
    }

    @Test
    fun `no route is registered twice, in one graph or across graphs`() {
        val all = graphs.flatMap { it.registered } + rootRegistered
        val dupes = all.groupBy { it }.filter { it.value.size > 1 }.keys
        assertWithMessage("Registered more than once (shared screens are registered once at the root): $dupes").that(dupes).isEmpty()
    }

    @Test
    fun `every tab root is registered in its own graph`() {
        graphs.forEach { g -> assertThat(g.registered).contains(g.name) }
        assertThat(tabRoots).isEqualTo(graphs.map { it.name }.toSet())
    }

    @Test
    fun `every pushed screen is wrapped in a frame`() {
        // Each registration (but a dialog, the welcome flow, cook mode and the workout) must open with PushedScreen,
        // TabRootScreen or FullScreenTask or call a composable that wraps itself (Today).
        val matches = registerRx.findAll(host).toList()
        val frames = listOf("PushedScreen", "TabRootScreen", "FullScreenTask", "SettingsGroupScreen", "TodayScreen", "SwapSheet")
        val missing = matches.mapIndexedNotNull { i, m ->
            val end = matches.getOrNull(i + 1)?.range?.first ?: host.length
            val text = host.substring(m.range.first, end)
            // Only the start of the registration: its first 400 characters.
            if (frames.none { it in text.take(400) }) m.groupValues[1] else null
        }
        assertWithMessage("Routes whose composable is not wrapped in a frame: $missing").that(missing).isEmpty()
    }

    @Test
    fun `every call site outside the host navigates to a registered route`() {
        val registered = registerRx.findAll(host).map { it.groupValues[1] }.toSet()
        val rx = Regex("""navigate\(\s*Route\.(\w+)|onNavigate\(Route\.(\w+)""")
        val bad = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "IntakeNavHost.kt" }.flatMap { f ->
            rx.findAll(f.readText()).map { (it.groupValues[1].ifEmpty { it.groupValues[2] }) to f.name }.filter { (r, _) -> r !in registered }
        }.toList()
        assertWithMessage("navigate() to unregistered routes: $bad").that(bad).isEmpty()
    }
}
