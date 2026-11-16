package com.zeroindex.intake.ui.screens.templates

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.repository.TemplateLogResult
import com.zeroindex.intake.data.repository.TemplateSummary
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.domain.template.TemplateLogMessage
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.nfc.NfcTagWriter
import com.zeroindex.intake.nfc.TagWriteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import com.zeroindex.intake.ui.allergen.chipDetail
import com.zeroindex.intake.ui.allergen.traceChipLabel
import com.zeroindex.intake.ui.allergen.verdict
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WriteState {
    data object Waiting : WriteState
    data class Done(val result: TagWriteResult) : WriteState
}

/** A template that matches the person's allergens, waiting on the warning sheet. Nothing is logged until "Log it anyway". */
data class PendingTemplateLog(
    val summary: TemplateSummary,
    val slot: MealSlot,
    val check: com.zeroindex.intake.data.repository.TemplateCheck,
)

data class TemplatesUiState(
    val writingFor: TemplateSummary? = null,
    val write: WriteState = WriteState.Waiting,
    val pending: PendingTemplateLog? = null,
)

@HiltViewModel
class MealTemplatesViewModel @Inject constructor(
    private val repository: MealTemplateRepository,
    private val allergens: com.zeroindex.intake.data.repository.AllergenRepository,
    private val messages: AppMessages,
) : ViewModel() {

    val templates: StateFlow<List<TemplateSummary>> =
        repository.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The allergen chip per template (by id): every food in it through the local engine, read again when the templates
     * or the person's allergens change. Empty when the person avoids nothing. A template with no ingredient text on its
     * foods reads "only the name was checked", never clear.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val verdicts: StateFlow<Map<String, com.zeroindex.intake.ui.addfood.RowAllergen>> = kotlinx.coroutines.flow.combine(
        templates, allergens.observeAvoidedAllergens(), allergens.observeAliases(),
    ) { list, _, _ -> list }.mapLatest { list ->
        if (list.isEmpty() || allergens.avoidedAllergens().isEmpty()) {
            emptyMap()
        } else {
            kotlinx.coroutines.withContext(Dispatchers.Default) {
                repository.verdicts(list).mapValues { (id, scan) ->
                    val summary = list.first { it.template.id == id }
                    com.zeroindex.intake.ui.addfood.recipeRowAllergenOf(
                        scan,
                        ingredientLineCount = summary.items.size,
                        readLineCount = summary.items.count { (f, _) -> f.allergenTags.isNotBlank() },
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    /**
     * "Log now": the template is scanned first (every food, as the profile is now). A CONTAINS or MAY CONTAIN match goes
     * to the standard warning sheet with "Log it anyway", never to a dead-end message.
     */
    fun logNow(t: TemplateSummary, slot: MealSlot) = viewModelScope.launch {
        val check = repository.checkOf(t.template.id)
        if (check != null && com.zeroindex.intake.domain.allergen.AllergenLogGate.decide(check.scan)
            is com.zeroindex.intake.domain.allergen.AllergenLogGate.Decision.Review
        ) {
            _state.update { it.copy(pending = PendingTemplateLog(t, slot, check)) }
            return@launch
        }
        logChecked(t, slot, confirmed = false)
    }

    /** "Log it anyway": logs every food and keeps "Logged despite: ..." on each flagged entry. */
    fun confirmPending() = viewModelScope.launch {
        val p = _state.value.pending ?: return@launch
        _state.update { it.copy(pending = null) }
        logChecked(p.summary, p.slot, confirmed = true)
    }

    fun dismissPending() = _state.update { it.copy(pending = null) }

    /** Logs the template and reports it as a snackbar with Undo, the same as a single food log. */
    private suspend fun logChecked(t: TemplateSummary, slot: MealSlot, confirmed: Boolean) {
        when (val r = repository.log(t.template.id, slot, allergenOverride = confirmed, noteDespite = confirmed)) {
            is TemplateLogResult.Logged -> messages.post(
                AppMessage(
                    text = TemplateLogMessage.logged(t.template.name, r.slot, r.allergenNote),
                    actionLabel = "Undo",
                    onAction = { repository.undo(r.logs) },
                ),
            )
            is TemplateLogResult.Blocked -> messages.post(AppMessage("Not logged: ${r.foods.joinToString()} now matches your allergen profile"))
            is TemplateLogResult.NeedsReview -> messages.post(AppMessage("Not logged: ${r.foods.joinToString()} may contain something you avoid."))
            TemplateLogResult.NotFound -> messages.post(AppMessage("That meal is empty."))
        }
    }

    fun delete(t: TemplateSummary) = viewModelScope.launch { repository.delete(t.template.id) }
}
