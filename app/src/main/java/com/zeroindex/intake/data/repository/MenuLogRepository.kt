package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.MenuDao
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.menu.MenuSearch
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An imported menu item found by Log food search, with today's allergen scan of it. */
data class MenuHit(
    val item: MenuItemEntity,
    val scan: AllergenScanResult,
    /** True when the item carries no ingredient text and no stated allergens, so a clear scan proves nothing. */
    val noIngredientInfo: Boolean,
)

/** What logging a menu item did. */
sealed interface MenuLogOutcome {
    data class Logged(val log: FoodLogEntity) : MenuLogOutcome

    /** The scan found something the person avoids: nothing was written, the warning sheet decides. */
    data class NeedsConfirmation(val name: String, val macros: Macros, val scan: AllergenScanResult) : MenuLogOutcome
}

/**
 * Imported restaurant menus in Log food (docs/FOOD-DATA.md, "Restaurant menus"). Everything is on this phone: the
 * search reads the saved menu items, and every scan runs here, off the main thread.
 */
@Singleton
class MenuLogRepository @Inject constructor(
    private val menuDao: MenuDao,
    private val allergens: AllergenRepository,
    private val foodLog: FoodLogRepository,
) {
    /**
     * Saved menu items matching [query] that have calories, best matches first, each scanned now. The stored verdict
     * can be stale, so it is not used here.
     */
    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<MenuHit> {
        if (query.isBlank()) return emptyList()
        val all = menuDao.allItems().filter { it.kcal != null }
        if (all.isEmpty()) return emptyList()
        val scanner = allergens.menuScanner()
        return withContext(Dispatchers.Default) {
            MenuSearch.search(all, query, name = { it.name }, restaurant = { it.restaurantName }, limit = limit).map { m ->
                MenuHit(m, scanner(m.name, m.description, m.ingredientsText), MenuSearch.hasNoIngredientInfo(m.ingredientsText))
            }
        }
    }

    /**
     * Logs one serving as a quick add with the item's own numbers, unless today's scan of the item finds something
     * the person avoids: then [MenuLogOutcome.NeedsConfirmation] carries what the warning sheet needs.
     */
    suspend fun log(itemId: String, slot: MealSlot, at: Long): MenuLogOutcome? {
        val m = menuDao.item(itemId) ?: return null
        val scan = withContext(Dispatchers.Default) { allergens.menuScanner()(m.name, m.description, m.ingredientsText) }
        val name = labelOf(m)
        val macros = macrosOf(m)
        if (scan.hasAnything) return MenuLogOutcome.NeedsConfirmation(name, macros, scan)
        return MenuLogOutcome.Logged(foodLog.quickAdd(name, macros, slot, at))
    }

    companion object {
        const val SEARCH_LIMIT = 25

        fun labelOf(m: MenuItemEntity) = "${m.name} · ${m.restaurantName}"

        /** One serving: the item's published numbers. A number the menu did not give is left at zero, not invented. */
        fun macrosOf(m: MenuItemEntity) = Macros(
            kcal = m.kcal ?: 0.0, protein = m.proteinG ?: 0.0, carbs = m.carbsG ?: 0.0, fat = m.fatG ?: 0.0,
        )
    }
}
