package com.zeroindex.intake.ui.addfood

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.BackgroundDispatchers
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.domain.model.FoodSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

/**
 * Q6-3: the Log food allergen scan was cancelled and started again on every emission of any list, search source or
 * tab, although the rows to scan were the same, so one burst of typing started it dozens of times.
 */
class ScanTargetsTest {
    private fun food(id: String, name: String = id) = FoodEntity(
        id = id, name = name, servingSizeGrams = 100.0, servingLabel = "100 g", kcalPer100g = 100.0,
        proteinPer100g = 1.0, carbsPer100g = 1.0, fatPer100g = 1.0, source = FoodSource.MANUAL,
        createdAt = 1, updatedAt = 1,
    )

    private fun targets(
        recents: List<FoodEntity> = emptyList(),
        favorites: List<FoodEntity> = emptyList(),
        local: List<FoodEntity> = emptyList(),
        mine: List<FoodEntity> = emptyList(),
        tab: AddFoodTab = AddFoodTab.SEARCH,
        off: OffUiState = OffUiState.Hidden,
        search: SearchState = SearchState.Idle,
    ) = ScanTargets.of(recents, favorites, local, mine, tab, off, search, emptyList())

    @Test
    fun `a food on screen twice is scanned once`() {
        val milk = food("milk")
        val t = targets(recents = listOf(milk, food("oats")), favorites = listOf(milk), local = listOf(food("milk")))
        assertThat(t.foods.map { it.id }).containsExactly("milk", "oats").inOrder()
    }

    @Test
    fun `rows from every source are scanned`() {
        val t = targets(
            recents = listOf(food("r")), favorites = listOf(food("f")), local = listOf(food("l")),
            off = OffUiState.Results(listOf(food("off"))), search = SearchState.Remote(listOf(food("usda"))),
        )
        assertThat(t.foods.map { it.id }).containsExactly("r", "f", "l", "off", "usda").inOrder()
    }

    @Test
    fun `the phone's whole food list is scanned only for the Mine tab`() {
        val all = listOf(food("a"), food("b"))
        assertThat(targets(mine = all, tab = AddFoodTab.SEARCH).foods).isEmpty()
        assertThat(targets(mine = all, tab = AddFoodTab.MY_FOODS).foods.map { it.id }).containsExactly("a", "b")
    }

    @Test
    fun `a food written elsewhere does not change the targets on the Search tab`() {
        val local = listOf(food("milk"))
        val before = targets(local = local, mine = listOf(food("milk")))
        val after = targets(local = local, mine = listOf(food("milk"), food("new food")))
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `the same rows in new lists are equal, so the scan is not started again`() = runBlocking {
        val states = listOf(
            targets(local = listOf(food("a"), food("b"))),
            targets(local = listOf(food("a"), food("b"))), // another emission of the same rows (drinks pointer, verdicts ...)
            targets(local = listOf(food("a"), food("b"))),
            targets(local = listOf(food("a"))), // the rows changed: scan again
            targets(local = listOf(food("a"))),
        )
        val starts = flowOf(*states.toTypedArray()).distinctUntilChanged().toList()
        assertThat(starts).hasSize(2)
    }

    @Test
    fun `a changed row is a new scan`() {
        assertThat(targets(local = listOf(food("a", "Almond milk")))).isNotEqualTo(targets(local = listOf(food("a", "Almond milk, plain"))))
    }

    @Test
    fun `list work runs on its own single background thread`() = runBlocking {
        val first = withContext(BackgroundDispatchers.scan) { Thread.currentThread() }
        val second = withContext(BackgroundDispatchers.scan) { Thread.currentThread() }
        assertThat(first).isSameInstanceAs(second)
        assertThat(first.isDaemon).isTrue()
        assertThat(first.name).startsWith("intake-scan")
    }
}
