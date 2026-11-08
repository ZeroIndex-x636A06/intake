package com.zeroindex.intake.ui.screens.templates

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.repository.TemplateLogResult
import com.zeroindex.intake.data.repository.TemplateSummary
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.nfc.NfcTagWriter
import com.zeroindex.intake.nfc.TagWriteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WriteState {
    data object Waiting : WriteState
    data class Done(val result: TagWriteResult) : WriteState
}

data class TemplatesUiState(
    val writingFor: TemplateSummary? = null,
    val write: WriteState = WriteState.Waiting,
    val message: String? = null,
)

@HiltViewModel
class MealTemplatesViewModel @Inject constructor(
    private val repository: MealTemplateRepository,
) : ViewModel() {

    val templates: StateFlow<List<TemplateSummary>> =
        repository.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(TemplatesUiState())
    val state: StateFlow<TemplatesUiState> = _state.asStateFlow()

    fun startWrite(t: TemplateSummary) = _state.update { it.copy(writingFor = t, write = WriteState.Waiting) }
    fun stopWrite() = _state.update { it.copy(writingFor = null) }

    /** Called from the reader-mode callback, on the NFC thread. */
    fun onTag(tag: Tag, packageName: String) {
        val target = _state.value.writingFor ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = NfcTagWriter.write(tag, TemplateLink.uriFor(target.template.id), packageName)
            if (result is TagWriteResult.Written) repository.markWritten(target.template.id, result.uid)
            _state.update { it.copy(write = WriteState.Done(result)) }
        }
    }

    fun logNow(t: TemplateSummary, slot: MealSlot) = viewModelScope.launch {
        val message = when (val r = repository.log(t.template.id, slot)) {
            is TemplateLogResult.Logged -> "Logged ${t.template.name} to ${slot.name.lowercase()}"
            is TemplateLogResult.Blocked -> "Not logged: ${r.foods.joinToString()} now matches your allergen profile"
            TemplateLogResult.NotFound -> "That meal is empty."
        }
        _state.update { it.copy(message = message) }
    }

    fun delete(t: TemplateSummary) = viewModelScope.launch { repository.delete(t.template.id) }
    fun clearMessage() = _state.update { it.copy(message = null) }
}
