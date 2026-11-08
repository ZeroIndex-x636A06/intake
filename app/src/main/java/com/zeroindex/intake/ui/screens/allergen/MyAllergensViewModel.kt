package com.zeroindex.intake.ui.screens.allergen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.AllergenAliasEntity
import com.zeroindex.intake.data.repository.AllergenOverview
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.domain.allergen.AllergenCatalog
import com.zeroindex.intake.domain.allergen.CheckLabelTerm
import com.zeroindex.intake.domain.allergen.HiddenCarriers
import com.zeroindex.intake.domain.model.AllergenSeverity
import com.zeroindex.intake.domain.model.KeywordLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the keyword screen of one allergen shows. */
data class AllergenKeywordsState(
    val loaded: Boolean = false,
    val key: String = "",
    val displayName: String = "",
    val summary: String = "",
    val isCustom: Boolean = false,
    val avoided: Boolean = false,
    val keywords: List<AllergenAliasEntity> = emptyList(),
    /** Built-in "check the label" reminders for this allergen: always on, listed so nothing is hidden. */
    val carriers: List<CheckLabelTerm> = emptyList(),
)

@HiltViewModel
class MyAllergensViewModel @Inject constructor(
    private val repository: AllergenRepository,
) : ViewModel() {

    val overview: StateFlow<AllergenOverview?> = repository.observeOverview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val message = MutableStateFlow<String?>(null)

    fun stateFor(key: String): Flow<AllergenKeywordsState> =
        combine(repository.observeDefinition(key), repository.observeKeywords(key), repository.observeAvoidedAllergens()) { def, words, avoided ->
            AllergenKeywordsState(
                loaded = true,
                key = key,
                displayName = def?.displayName ?: AllergenCatalog.displayName(key),
                summary = def?.description.orEmpty(),
                isCustom = def?.isCustom ?: true,
                avoided = key in avoided,
                keywords = words,
                carriers = HiddenCarriers.forAllergen(key),
            )
        }

    fun setAvoided(key: String, avoid: Boolean) = viewModelScope.launch { repository.setAvoided(key, avoid) }

    fun setSeverity(severity: AllergenSeverity) = viewModelScope.launch { repository.setSeverity(severity) }

    fun createAllergen(name: String, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        val key = repository.createCustomAllergen(name)
        if (key == null) message.value = "Give the allergen a name first." else {
            message.value = "Added ${name.trim()} and turned it on."
            onCreated(key)
        }
    }

    fun removeAllergen(key: String) = viewModelScope.launch { repository.removeCustomAllergen(key) }

    fun addKeyword(key: String, term: String, level: KeywordLevel) = viewModelScope.launch {
        if (term.isBlank()) {
            message.value = "Type the word first."
        } else {
            repository.addKeyword(key, term, level)
            message.value = "Added \"${term.trim().lowercase()}\"."
        }
    }

    fun setKeywordEnabled(id: String, enabled: Boolean) = viewModelScope.launch { repository.setKeywordEnabled(id, enabled) }

    fun setKeywordLevel(id: String, level: KeywordLevel) = viewModelScope.launch { repository.setKeywordLevel(id, level) }

    fun removeKeyword(id: String) = viewModelScope.launch { repository.removeKeyword(id) }

    fun consumeMessage() { message.value = null }
}
