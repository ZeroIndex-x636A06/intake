package com.zeroindex.intake.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.repository.ShoppingBuilderRepository
import com.zeroindex.intake.data.repository.TemplateSummary
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.plan.StandaloneList
import com.zeroindex.intake.domain.plan.StandaloneShopping
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.allergen.chipDetail
import com.zeroindex.intake.ui.allergen.traceChipLabel
import com.zeroindex.intake.ui.allergen.verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    recipeDao: RecipeDao,
    templateRepository: MealTemplateRepository,
    settings: SettingsRepository,
    private val builder: ShoppingBuilderRepository,
    private val allergens: com.zeroindex.intake.data.repository.AllergenRepository,
    private val messages: com.zeroindex.intake.ui.AppMessages,
) : ViewModel() {
    /** "Cleared" with an Undo that puts the picks and ticks back. Nothing is stored or deleted: the picks are screen state. */
    fun cleared(restore: () -> Unit) {
        messages.post(com.zeroindex.intake.ui.AppMessage("Shopping list cleared", "Undo") { restore() })
    }

    val recipes: StateFlow<List<RecipeEntity>> =
        recipeDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<TemplateSummary>> =
        templateRepository.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weightUnit: StateFlow<WeightUnit> =
        settings.settings.map { it.weightUnit }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.POUNDS)

    /** The list for the picks (id to how many times it is made). Empty picks give an empty list. */
    suspend fun build(recipePicks: Map<String, Int>, templatePicks: Map<String, Int>): StandaloneList =
        if (recipePicks.isEmpty() && templatePicks.isEmpty()) {
            StandaloneList(emptyList(), emptyList())
        } else {
            StandaloneShopping.build(builder.contributions(recipePicks, templatePicks))
        }

    /**
     * The allergen marker of each line (by line key) that matches one of the person's allergens: the local engine on the
     * item name, trace wording where the match is a trace. Lines that match nothing have none. Nothing is hidden.
     */
    suspend fun marks(list: StandaloneList): Map<String, com.zeroindex.intake.ui.addfood.RowAllergen> {
        val lines = list.sections.flatMap { it.second } + list.pantry
        if (lines.isEmpty()) return emptyMap()
        val scans = com.zeroindex.intake.domain.plan.ShoppingAllergenMarks.flagged(
            allergens.shoppingScans(lines.map { it.name }), includeCheckLabel = true,
        )
        return lines.mapNotNull { l ->
            scans[l.name]?.let { sc ->
                l.key to com.zeroindex.intake.ui.addfood.RowAllergen(sc.verdict(), sc.chipDetail(), traceLabel = sc.traceChipLabel())
            }
        }.toMap()
    }
}
