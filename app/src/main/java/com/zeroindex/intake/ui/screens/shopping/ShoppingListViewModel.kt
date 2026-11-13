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
) : ViewModel() {
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
}
