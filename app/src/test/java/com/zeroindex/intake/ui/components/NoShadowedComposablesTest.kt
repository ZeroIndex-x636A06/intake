package com.zeroindex.intake.ui.components

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Two public composables with one name in one package are a trap: `EmptyState(title = ..., body = ...)` once bound
 * to a whole-screen `fillMaxSize().verticalScroll()` variant (its non-null `title` is the more specific overload)
 * instead of the in-list one, and crashed with "infinity maximum height" inside every LazyColumn that used it
 * (Menu import, Meal templates, Health Connect, Training stats). Give a different shape a different name.
 */
class NoShadowedComposablesTest {

    private val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }

    @Test
    fun `a package declares each public composable name once`() {
        val rx = Regex("""(?m)^@Composable\s*\n(?:@[^\n]*\n)*fun (\w+)\(""")
        val dupes = sourceRoot.walkTopDown().filter { it.isDirectory }.flatMap { dir ->
            val names = dir.listFiles { f -> f.isFile && f.extension == "kt" }.orEmpty()
                .flatMap { f -> rx.findAll(f.readText()).map { it.groupValues[1] to f.name }.toList() }
            names.groupBy({ it.first }, { it.second }).filter { it.value.size > 1 }
                .map { "${dir.name}/${it.key} in ${it.value}" }
        }.toList()
        assertWithMessage("Same-named public composables in one package: $dupes").that(dupes).isEmpty()
    }
}
