package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Fails when a source file that creates a food log entry is not accounted for here, so a new log path cannot skip the
 * allergen gate unnoticed (QA Q2-1). Every path that writes a food log must scan with the local engine
 * (`AllergenLogGate`, `docs/ALLERGENS.md`) and send a flagged result to the warning sheet; this test pins the list of
 * files that write, and that each file listed as scanning still contains a scan.
 *
 * To add a log path: put the scan and the warning sheet in front of it, then add its file below with a one-line note.
 */
class LogPathAuditTest {

    private enum class Gate { SCANS, DELEGATED, EXEMPT }

    private data class Entry(val gate: Gate, val note: String)

    /** Paths are relative to `com/zeroindex/intake`. */
    private val known: Map<String, Entry> = mapOf(
        // Phone: Log food, food detail, drinks, recipes
        "ui/addfood/AddFoodViewModel.kt" to Entry(Gate.SCANS, "search rows, +, quick add, menu items: requestLogFood / requestQuickAdd / pendingLog"),
        "ui/addfood/RecipeSearchViewModel.kt" to Entry(Gate.SCANS, "Log food recipe rows: logger.scan then PendingRecipeLog"),
        "ui/screens/fooddata/FoodDetailViewModel.kt" to Entry(Gate.SCANS, "food detail Log: scan then pending sheet"),
        "ui/screens/drinks/DrinksViewModel.kt" to Entry(Gate.SCANS, "Drinks: scan then PendingDrink"),
        "ui/screens/drinks/CustomDrinkViewModel.kt" to Entry(Gate.SCANS, "custom drink: scan then PendingDrink"),
        "ui/screens/plan/CookModeScreen.kt" to Entry(Gate.SCANS, "cook mode Log a serving: logger.scan then PendingRecipeLog"),
        "ui/screens/plan/RecipesViewModel.kt" to Entry(Gate.SCANS, "recipe page Log a serving (Q2-1): AllergenLogGate then PendingServing"),
        "ui/screens/plan/MealPlanViewModel.kt" to Entry(Gate.SCANS, "plan Log this meal and eat-out options: scanMeal / scanEatOut then sheet"),
        "ui/screens/plan/PlanScreen.kt" to Entry(Gate.DELEGATED, "forwards the eat-out tap to MealPlanViewModel.logEatOut, which scans"),
        "ui/screens/today/TodayExtrasViewModel.kt" to Entry(Gate.SCANS, "Today next-meal card: scanMeal then pending"),
        "ui/screens/nutrition/NutritionHomeViewModel.kt" to Entry(Gate.SCANS, "Nutrition next-meal card: scanMeal then pending"),
        "ui/screens/nutrition/NutritionMealViewModel.kt" to Entry(Gate.SCANS, "copy to meal and quick add calories: AllergenLogGate then PendingEntryAllergen"),
        "ui/screens/today/TodayViewModel.kt" to Entry(Gate.SCANS, "copy to meal: AllergenLogGate then PendingEntryAllergen"),
        // Repositories that do the write for a caller above; the caller holds the gate (listed above)
        "data/repository/RecipeLogger.kt" to Entry(Gate.SCANS, "exposes scan(); every caller gates on it"),
        "data/repository/MealPlanRepository.kt" to Entry(Gate.SCANS, "logMeal rescans and refuses CONTAINS; scanMeal / scanEatOut for the sheet"),
        "data/repository/MealTemplateRepository.kt" to Entry(Gate.SCANS, "log() rescans every food: Blocked / NeedsReview unless overridden"),
        "data/repository/MenuLogRepository.kt" to Entry(Gate.SCANS, "log() rescans the item: NeedsConfirmation"),
        "data/repository/DrinkLogger.kt" to Entry(Gate.DELEGATED, "writes for DrinkRepository; callers scan first"),
        "data/repository/DrinkRepository.kt" to Entry(Gate.DELEGATED, "store for DrinkLogger; callers scan first"),
        "data/swap/SwapRepository.kt" to Entry(Gate.SCANS, "choose(): blocked options refused, rescans eat-out and menu items"),
        "data/ai/planb/PlanBFinder.kt" to Entry(Gate.SCANS, "log() is only reached from SwapRepository.choose"),
        "data/ai/vision/VisionTracker.kt" to Entry(Gate.SCANS, "photo estimate: recheck() then confirmingAllergens"),
        "MainActivity.kt" to Entry(Gate.DELEGATED, "NFC tag: templates.log refuses CONTAINS and needs review for MAY_CONTAIN"),
        // Watch
        "wear/RemoteRequestHandler.kt" to Entry(Gate.SCANS, "AllergenLogGate.forWatch for foods and planned meals; templates.log refuses flagged ones"),
        // The row type itself
        "data/db/entity/FoodEntities.kt" to Entry(Gate.EXEMPT, "defines FoodLogEntity; writes nothing"),
    )

    /** What each file listed as SCANS must still contain: some form of scan or gate. */
    private val scanTokens = listOf("scan", "Scan", "AllergenLogGate", "decide(", "NeedsConfirmation", "blocked")

    private val writeCall = Regex(
        """\.(logFood|quickAdd|logPrepared|copyToSlot|logServings|logEatOut|logMeal|saveFoodLog|logCustom)\(|FoodLogEntity\(|foodLogDao\.upsert|[dD]rinks?\w*\.log\(|[tT]emplates?\w*\.log\(|menuLogRepository\.log\(""",
    )

    private fun sourceRoot(): File {
        // Gradle runs unit tests with the module directory (app/) as the working directory.
        val candidates = listOf(File("src/main/java/com/zeroindex/intake"), File("app/src/main/java/com/zeroindex/intake"))
        return candidates.firstOrNull { it.isDirectory } ?: error("main source root not found from ${File(".").absolutePath}")
    }

    @Test fun `every file that writes a food log is accounted for`() {
        val root = sourceRoot()
        val writers = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.relativeTo(root).invariantSeparatorsPath != "data/repository/FoodLogRepository.kt" }
            .filter { writeCall.containsMatchIn(it.readText()) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSortedSet()

        val unknown = writers - known.keys
        assertWithMessage(
            "These files write food log entries but are not in LogPathAuditTest. Add the allergen scan and warning sheet " +
                "(AllergenLogGate) in front of the write, then list the file: $unknown",
        ).that(unknown).isEmpty()

        val stale = known.keys - writers
        assertWithMessage("These entries no longer write a food log; remove them from LogPathAuditTest: $stale").that(stale).isEmpty()
    }

    @Test fun `every file listed as scanning still scans`() {
        val root = sourceRoot()
        val missing = known.filter { it.value.gate == Gate.SCANS }.keys.filter { path ->
            val text = File(root, path).takeIf { it.isFile }?.readText().orEmpty()
            scanTokens.none { text.contains(it) }
        }
        assertWithMessage("These files are listed as scanning but contain no scan or gate: $missing").that(missing).isEmpty()
    }

    @Test fun `the watch handler gates planned meals and foods with the shared gate`() {
        val text = File(sourceRoot(), "wear/RemoteRequestHandler.kt").readText()
        // Each place the watch can write a food log passes through AllergenLogGate.forWatch or a repository that refuses.
        assertWithMessage("watch food and planned-meal logs must use AllergenLogGate.forWatch")
            .that(Regex("AllergenLogGate\\.forWatch").findAll(text).count()).isAtLeast(2)
        assertWithMessage("the watch must not ask a repository to note-and-log a flagged item on its own")
            .that(text.contains("noteDespite = true")).isFalse()
    }
}
