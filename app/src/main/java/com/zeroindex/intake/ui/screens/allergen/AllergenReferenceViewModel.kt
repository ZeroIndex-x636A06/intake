package com.zeroindex.intake.ui.screens.allergen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.rank
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.MealPlanRepository
import com.zeroindex.intake.data.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One item that was flagged: a recipe or a saved eat-out option, with the worst status the local engine gave it. */
data class FlaggedItem(val id: String, val name: String, val kind: String, val status: AllergenStatus, val updatedAt: Long)

data class AllergenReferenceUiState(
    val avoided: Set<String> = emptySet(),
    val aliasesByAllergen: Map<String, List<String>> = emptyMap(),
    /** "Check the label" terms, grouped by the reason shown with each. */
    val checkLabelByReason: Map<String, List<String>> = emptyMap(),
    /** Recipes and eat-out options the local engine marked CONTAINS or MAY CONTAIN, newest first. */
    val flagged: List<FlaggedItem> = emptyList(),
)

@HiltViewModel
class AllergenReferenceViewModel @Inject constructor(
    allergenRepository: AllergenRepository,
    planRepository: PlanRepository,
    mealPlanRepository: MealPlanRepository,
) : ViewModel() {

    val uiState: StateFlow<AllergenReferenceUiState> = combine(
        allergenRepository.observeAvoidedAllergens(),
        allergenRepository.observeAliasesByAllergen(),
        planRepository.observeAllEatOutOptions(),
        allergenRepository.observeCheckLabelTerms(),
        mealPlanRepository.observeRecipes(),
    ) { avoided, aliases, options, soft, recipes ->
        val flagged = (
            recipes.mapNotNull { r -> r.allergenStatus?.takeIf { it.rank >= AllergenStatus.MAY_CONTAIN.rank }?.let { FlaggedItem(r.id, r.name, "Recipe", it, r.updatedAt) } } +
                options.mapNotNull { o -> o.allergenStatus?.takeIf { it.rank >= AllergenStatus.MAY_CONTAIN.rank }?.let { FlaggedItem(o.id, "${o.venueName}: ${o.whatToOrder}", "Eat out", it, o.updatedAt) } }
            ).sortedByDescending { it.updatedAt }.take(8)
        AllergenReferenceUiState(
            avoided = avoided,
            aliasesByAllergen = aliases,
            checkLabelByReason = soft.groupBy({ it.reason }, { it.phrase.replace(" ... ", " ") }),
            flagged = flagged,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AllergenReferenceUiState(),
    )
}
