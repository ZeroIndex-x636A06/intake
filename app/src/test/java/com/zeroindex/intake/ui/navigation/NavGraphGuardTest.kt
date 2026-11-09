package com.zeroindex.intake.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * A pure JVM guard on the navigation graph, by scanning the sources the way `AiAuditTest` does.
 *
 * It catches the class of crash where `navigate(Route.X)` has no `composable<Route.X>` behind it
 * ("Navigation destination ... cannot be found"), and the quieter one where X is registered only in another
 * tab's graph, so the push silently switches tabs and the pill lights the wrong one.
 */
class NavGraphGuardTest {

    private val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
    private val navDir = sourceRoot.resolve("com/zeroindex/intake/ui/navigation")
    private val host = navDir.resolve("IntakeNavHost.kt").readText()
    private val destinations = navDir.resolve("Destinations.kt").readText()

    /** Screens outside every tab graph on purpose. */
    private val outsideTabs = setOf("Onboarding")

    private val composableRx = Regex("""composable<Route\.(\w+)>""")
    private val navigateRx = Regex("""navigate\(Route\.(\w+)""")

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

    private class Scan(val registered: List<String>, val navigated: Set<String>)

    /** Private `NavGraphBuilder.name(...)` helpers: what each registers and what its screens navigate to. */
    private val helpers: Map<String, Scan> by lazy {
        Regex("""private fun NavGraphBuilder\.(\w+)\(""").findAll(host).associate { m ->
            val open = host.indexOf('{', host.indexOf(')', m.range.last))
            val body = block(host, open)
            m.groupValues[1] to Scan(composableRx.findAll(body).map { it.groupValues[1] }.toList(), navigateRx.findAll(body).map { it.groupValues[1] }.toSet())
        }
    }

    private class Graph(val name: String, val registered: List<String>, val navigated: Set<String>)

    private val graphs: List<Graph> by lazy {
        Regex("""navigation<TabGraph\.(\w+)>\(startDestination = Route\.\w+\) \{""").findAll(host).map { m ->
            val body = block(host, m.range.last)
            val registered = composableRx.findAll(body).map { it.groupValues[1] }.toMutableList()
            val navigated = navigateRx.findAll(body).map { it.groupValues[1] }.toMutableSet()
            helpers.forEach { (name, scan) ->
                if (Regex("""(?m)^\s*$name\(""").containsMatchIn(body)) {
                    registered += scan.registered
                    navigated += scan.navigated
                }
            }
            Graph(m.groupValues[1], registered, navigated)
        }.toList()
    }

    private val routes: Set<String> by lazy {
        Regex("""@Serializable data (?:object|class) (\w+)\(?[^\n]*: Route""").findAll(destinations).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `the scan sees the five tab graphs and the routes`() {
        assertThat(graphs.map { it.name }).containsExactly("Today", "Food", "Train", "Progress", "More")
        assertThat(routes).containsAtLeast("MealTemplates", "MenuSources", "HealthConnect", "WorkoutHistory")
    }

    @Test
    fun `every route has a composable`() {
        val registered = composableRx.findAll(host).map { it.groupValues[1] }.toSet() +
            helpers.values.flatMap { it.registered }
        assertWithMessage("Routes with no composable<...> in IntakeNavHost").that(routes - registered).isEmpty()
    }

    @Test
    fun `every navigate in a tab graph targets a route registered in that same graph`() {
        val problems = graphs.flatMap { g ->
            (g.navigated - g.registered.toSet() - outsideTabs).map { "${g.name} graph navigates to Route.$it but does not register it" }
        }
        assertWithMessage(problems.joinToString("\n")).that(problems).isEmpty()
    }

    @Test
    fun `no route is registered twice in one graph`() {
        val dupes = graphs.flatMap { g -> g.registered.groupBy { it }.filter { it.value.size > 1 }.keys.map { "${g.name}: $it" } }
        assertWithMessage("Duplicate registration in a graph").that(dupes).isEmpty()
    }

    @Test
    fun `every call site outside the host navigates to a registered route`() {
        val registered = composableRx.findAll(host).map { it.groupValues[1] }.toSet() + helpers.values.flatMap { it.registered }
        val rx = Regex("""navigate\(\s*Route\.(\w+)|onNavigate\(Route\.(\w+)""")
        val bad = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "IntakeNavHost.kt" }.flatMap { f ->
            rx.findAll(f.readText()).map { (it.groupValues[1].ifEmpty { it.groupValues[2] }) to f.name }.filter { (r, _) ->
                r !in registered && r !in setOf("Today", "Food", "Train", "Progress", "More")
            }
        }.toList()
        assertWithMessage("navigate() to unregistered routes: $bad").that(bad).isEmpty()
    }
}
