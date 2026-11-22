package com.zeroindex.intake.ui.addfood

import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.foodref.RefFood

/**
 * What the allergen scan of Log food covers: every food row on screen from every source, and the bundled USDA rows.
 * A data class so two builds of the same rows are equal, and the scan is not started again for them (Q6-3).
 */
internal data class ScanTargets(val foods: List<FoodEntity>, val refs: List<RefFood>) {
    companion object {
        /**
         * Every row on screen, from every source: this phone (recents, favourites, matches), Open Food Facts and the
         * USDA API. The phone's whole food list is only read for the Mine tab, so a food written elsewhere does not
         * change the targets on the other tabs.
         */
        fun of(
            recents: List<FoodEntity>,
            favorites: List<FoodEntity>,
            localResults: List<FoodEntity>,
            mine: List<FoodEntity>,
            tab: AddFoodTab,
            off: OffUiState,
            search: SearchState,
            refResults: List<RefFood>,
        ): ScanTargets {
            val foods = (
                recents + favorites + localResults +
                    (if (tab == AddFoodTab.MY_FOODS) mine else emptyList()) +
                    (off as? OffUiState.Results)?.foods.orEmpty() + (search as? SearchState.Remote)?.results.orEmpty()
                ).distinctBy { it.id }
            return ScanTargets(foods, refResults)
        }
    }
}
