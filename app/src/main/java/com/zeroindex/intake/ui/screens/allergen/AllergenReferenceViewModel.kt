package com.zeroindex.intake.ui.screens.allergen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AllergenReferenceUiState(
    val avoided: Set<String> = emptySet(),
    val aliasesByAllergen: Map<String, List<String>> = emptyMap(),
    val eatOutOptions: List<EatOutOptionEntity> = emptyList(),
)

@HiltViewModel
class AllergenReferenceViewModel @Inject constructor(
    allergenRepository: AllergenRepository,
    planRepository: PlanRepository,
) : ViewModel() {

    val uiState: StateFlow<AllergenReferenceUiState> = combine(
        allergenRepository.observeAvoidedAllergens(),
        allergenRepository.observeAliasesByAllergen(),
        planRepository.observeAllEatOutOptions(),
    ) { avoided, aliases, options ->
        AllergenReferenceUiState(avoided, aliases, options)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AllergenReferenceUiState(),
    )
}
