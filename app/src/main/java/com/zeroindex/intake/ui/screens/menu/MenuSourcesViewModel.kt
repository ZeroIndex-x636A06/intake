package com.zeroindex.intake.ui.screens.menu

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.menu.MenuIngestor
import com.zeroindex.intake.data.ai.menu.MenuProgress
import com.zeroindex.intake.data.ai.menu.MenuImportException
import com.zeroindex.intake.data.ai.menu.MenuNoTableException
import com.zeroindex.intake.data.ai.menu.PdfRoute
import com.zeroindex.intake.data.ai.menu.StagedItem
import com.zeroindex.intake.data.db.dao.MenuDao
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.MenuRunStatus
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.MenuSourceKind
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.menu.DownloadClassifier
import com.zeroindex.intake.data.ai.menu.MenuNutritionMerge
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.menu.NutritionPrompt
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Review(
    val source: MenuSourceEntity,
    val items: List<StagedItem>,
    val warnings: List<String> = emptyList(),
    /** True when the items were read on this phone from the PDF's text, with no AI. */
    val readOnPhone: Boolean = false,
    /** Set for an allergen chart: shown in place of the item count. */
    val chartNote: String? = null,
)

/** The phone's reader found no table in a PDF: [reason] says why, and the person may choose to use AI instead. */
data class AiOffer(val source: MenuSourceEntity, val reason: String)

/**
 * The phone's reader found no table in the nutrition menu the person gave (a picked PDF, or a link to one). Nothing was
 * sent anywhere. [chart] is the allergen chart it was for and [staging] the unsaved source; the person may choose AI.
 */
data class NutritionAiOffer(val chart: MenuSourceEntity, val staging: MenuSourceEntity, val reason: String)

data class MenuUiState(
    /** Where the import in flight is up to; null when none is running. */
    val running: MenuProgress? = null,
    val review: Review? = null,
    val aiOffer: AiOffer? = null,
    val error: String? = null,
    /** The link whose download was refused: the error card offers to choose the saved PDF instead. */
    val refusedSource: MenuSourceEntity? = null,
    /** An allergen chart was just saved: ask for the nutrition menu (Choose a PDF, Paste a link, Skip). */
    val nutritionPrompt: MenuSourceEntity? = null,
    /** The nutrition menu could not be read on the phone: the follow-up card offers "Read it with AI" (a tap, never automatic). */
    val nutritionAiOffer: NutritionAiOffer? = null,
    val hasKey: Boolean = false,
    val openSourceId: String? = null,
    /** The source whose "Discard import" is waiting for the confirm. */
    val confirmDiscard: MenuSourceEntity? = null,
)

@HiltViewModel
class MenuSourcesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val menuDao: MenuDao,
    private val ingestor: MenuIngestor,
    private val gateway: AiGateway,
    private val clock: Clock,
    private val settings: SettingsRepository,
) : ViewModel() {

    val sources: StateFlow<List<MenuSourceEntity>> =
        menuDao.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sources that are allergen charts with no calories yet: they get the small "Add nutrition menu" action. */
    val allergenOnlyIds: StateFlow<Set<String>> =
        menuDao.observeAllergenOnlySourceIds().map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _state = MutableStateFlow(MenuUiState(hasKey = gateway.isConfigured()))
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val openItems: StateFlow<List<MenuItemEntity>> = _state
        .flatMapLatest { s -> s.openSourceId?.let(menuDao::observeItemsOf) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, kind: MenuSourceKind, location: String, pasted: String?) = viewModelScope.launch {
        if (kind == MenuSourceKind.PDF) {
            // Keep read access to the picked file across restarts, so the
            // weekly run can re-read it.
            runCatching {
                context.contentResolver.takePersistableUriPermission(Uri.parse(location), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val now = clock.nowMillis()
        val source = MenuSourceEntity(
            restaurantName = name.trim(),
            kind = kind,
            location = location.trim(),
            pastedText = pasted,
            createdAt = now,
            updatedAt = now,
        )
        menuDao.upsertSource(source)
        run(source)
    }

    private var importJob: Job? = null

    /**
     * Imports [source]. The work runs off the main thread (see [MenuIngestor.stage]); progress is shown with a
     * Cancel that stops the download or the request in flight and puts the source back as it was.
     */
    fun run(source: MenuSourceEntity, pdfRoute: PdfRoute = PdfRoute.PHONE_ONLY) {
        // A link is tried even with AI off: a page that publishes its menu as structured data is read with no model,
        // and one that does not is refused by the gateway inside the import, with the same reason. A picked PDF is read
        // on the phone first (no model), so it only needs AI when the person chose it. Pasted text always needs the model.
        val needsModelNow = source.kind == MenuSourceKind.TEXT || (source.kind == MenuSourceKind.PDF && pdfRoute == PdfRoute.AI_ONLY)
        if (needsModelNow) {
            gateway.unavailableReason()?.let { reason ->
                _state.update { it.copy(error = reason.message) }
                return
            }
        }
        if (importJob?.isActive == true) return
        _state.update { it.copy(running = MenuProgress("Starting…"), error = null, refusedSource = null, aiOffer = null) }
        importJob = viewModelScope.launch {
            menuDao.upsertSource(source.copy(lastStatus = MenuRunStatus.RUNNING, updatedAt = clock.nowMillis()))
            try {
                val outcome = ingestor.stage(source, AiTrigger.USER_ACTION, onProgress = { progress ->
                    _state.update { s -> if (s.running != null) s.copy(running = progress) else s }
                }, pdfRoute = pdfRoute)
                keepForReview(source, outcome.items)
                _state.update { it.copy(running = null, review = Review(source, outcome.items, outcome.warnings, outcome.readOnPhone, outcome.chartNote)) }
            } catch (e: MenuNoTableException) {
                // Nothing was sent anywhere. Say why, and let the person choose the AI path.
                ingestor.markFailed(source, e.message ?: "No nutrition table was found.")
                _state.update { it.copy(running = null, aiOffer = AiOffer(source, e.message ?: "No nutrition table was found.")) }
            } catch (e: CancellationException) {
                // Cancel was pressed: the source goes back to how it was before this run.
                withContext(NonCancellable) { menuDao.upsertSource(source) }
                _state.update { it.copy(running = null) }
                throw e
            } catch (e: Exception) {
                val reason = (e as? AiError)?.message ?: (e as? MenuImportException)?.message ?: "The import failed. Try again, or paste the menu text."
                ingestor.markFailed(source, reason)
                val refused = source.takeIf { it.kind == MenuSourceKind.URL && DownloadClassifier.offersFilePicker(reason) }
                _state.update { it.copy(running = null, error = reason, refusedSource = refused) }
            }
        }
    }

    /** The person chose AI after the phone found no table: the same source, straight to the AI path. */
    fun readWithAi() {
        val offer = _state.value.aiOffer ?: return
        run(offer.source, PdfRoute.AI_ONLY)
    }

    fun dismissAiOffer() = _state.update { it.copy(aiOffer = null) }

    /** Stops the import in flight; the OkHttp download or model call is cancelled with it. */
    fun cancelImport() {
        importJob?.cancel()
    }

    fun toggle(index: Int) = _state.update { s ->
        val r = s.review ?: return@update s
        s.copy(review = r.copy(items = r.items.mapIndexed { i, it -> if (i == index) it.copy(accepted = !it.accepted) else it }))
    }

    /** Every finished import is kept for review before the screen opens, so closing it by accident loses nothing. */
    private suspend fun keepForReview(source: MenuSourceEntity, items: List<StagedItem>) {
        try {
            ingestor.persistStaged(source, items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The review still opens from memory; it just cannot be reopened later.
        }
    }

    fun commit() {
        val r = _state.value.review ?: return
        viewModelScope.launch {
            ingestor.commitStaged(r.source, r.items)
            // An allergen chart has no calories: ask for the nutrition menu, unless the person skipped it for this source.
            val skipped = r.source.id in settings.menuNutritionPromptSkipped.first()
            val prompt = NutritionPrompt.shouldPrompt(menuDao.itemsOf(r.source.id).map { it.kcal }, skipped)
            _state.update { it.copy(review = null, nutritionPrompt = if (prompt) r.source else it.nutritionPrompt) }
        }
    }

    /** Skip: dismiss the card for this source. The row keeps a small "Add nutrition menu" action. */
    fun skipNutritionPrompt() {
        val source = _state.value.nutritionPrompt ?: return
        _state.update { it.copy(nutritionAiOffer = null) }
        viewModelScope.launch { settings.skipMenuNutritionPrompt(source.id) }
        _state.update { it.copy(nutritionPrompt = null) }
    }

    /** The row action: show the card again for [source], whether or not it was skipped. */
    fun askForNutrition(source: MenuSourceEntity) = _state.update { it.copy(nutritionPrompt = source, openSourceId = null) }

    /** A nutrition PDF the person chose (the file picker). */
    fun addNutritionPdf(uri: String) = addNutrition(MenuSourceKind.PDF, uri)

    /** A link to the nutrition menu: it goes through the same download checks and refusal messages as any link. */
    fun addNutritionLink(url: String) = addNutrition(MenuSourceKind.URL, url.trim())

    /**
     * Reads the nutrition menu as a source that is never saved, then shows a review of the two files merged into the
     * chart's own source. Nothing is stored until the person accepts.
     */
    private fun addNutrition(kind: MenuSourceKind, location: String) {
        val chart = _state.value.nutritionPrompt ?: return
        val staging = chart.copy(id = com.zeroindex.intake.data.db.entity.newId(), kind = kind, location = location, pastedText = null)
        stageNutrition(chart, staging, PdfRoute.PHONE_ONLY)
    }

    /**
     * "Read it with AI" on the follow-up card: the same file or link, straight to the AI path. Only runs from this tap;
     * the model's numbers are read as the main import reads them (nothing invented, allergens can only be raised).
     */
    fun readNutritionWithAi() {
        val offer = _state.value.nutritionAiOffer ?: return
        gateway.unavailableReason()?.let { reason ->
            _state.update { it.copy(error = reason.message) }
            return
        }
        _state.update { it.copy(nutritionPrompt = offer.chart) }
        stageNutrition(offer.chart, offer.staging, PdfRoute.AI_ONLY)
    }

    fun dismissNutritionAiOffer() = _state.update { it.copy(nutritionAiOffer = null) }

    private fun stageNutrition(chart: MenuSourceEntity, staging: MenuSourceEntity, route: PdfRoute) {
        if (importJob?.isActive == true) return
        _state.update { it.copy(running = MenuProgress("Starting…"), error = null, refusedSource = null, aiOffer = null, nutritionAiOffer = null) }
        importJob = viewModelScope.launch {
            try {
                val outcome = ingestor.stage(staging, AiTrigger.USER_ACTION, onProgress = { progress ->
                    _state.update { s -> if (s.running != null) s.copy(running = progress) else s }
                }, pdfRoute = route)
                val nutrition = outcome.items.map { it.entity }
                if (NutritionPrompt.isAllergenOnly(nutrition.map { it.kcal })) {
                    _state.update { it.copy(running = null, error = "That file has no calories either: it is another allergen chart. Choose the nutrition menu.") }
                    return@launch
                }
                val merged = MenuNutritionMerge.merge(menuDao.itemsOf(chart.id), nutrition, chart.id, clock.nowMillis())
                val warnings = outcome.warnings + buildList {
                    if (merged.nutritionOnly > 0) add("${merged.nutritionOnly} item${if (merged.nutritionOnly == 1) "" else "s"} in the nutrition menu are not in the allergen chart. They are kept with their own allergen information only.")
                }
                keepForReview(chart, merged.items.map(::StagedItem))
                _state.update {
                    it.copy(
                        running = null, nutritionPrompt = null,
                        review = Review(chart, merged.items.map(::StagedItem), warnings, readOnPhone = false, chartNote = merged.summary),
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(running = null) }
                throw e
            } catch (e: MenuNoTableException) {
                // Nothing was sent anywhere. Say why, and let the person choose the AI path.
                _state.update { it.copy(running = null, nutritionAiOffer = NutritionAiOffer(chart, staging, e.message ?: "No nutrition table was found.")) }
            } catch (e: Exception) {
                val reason = (e as? AiError)?.message ?: (e as? MenuImportException)?.message ?: "The nutrition menu could not be read. Try again."
                _state.update { it.copy(running = null, error = reason) }
            }
        }
    }

    /** Closing the review (back, a swipe, a tap outside) keeps the import: the source reads "To review" until it is decided. */
    fun closeReview() = _state.update { it.copy(review = null) }

    /** The source row's "Review import": reopens the review with every staged item. */
    fun reviewStaged(source: MenuSourceEntity) {
        viewModelScope.launch {
            val items = ingestor.loadStaged(source)
            if (items.isEmpty()) {
                ingestor.discardStaged(source)
                _state.update { it.copy(error = "The staged import is empty, so it was cleared. Import the menu again.") }
                return@launch
            }
            val chart = items.none { it.entity.kcal != null }
            _state.update {
                it.copy(
                    openSourceId = null,
                    review = Review(source, items, chartNote = if (chart) "Allergen chart: ${items.size} items, no calories or macros in this file." else null),
                )
            }
        }
    }

    /** Asks for the confirm before "Discard import". */
    fun askDiscard(source: MenuSourceEntity?) = _state.update { it.copy(confirmDiscard = source) }

    /** "Discard import", confirmed: the staged items are soft deleted. This is the only way a staged import is removed. */
    fun discardImport(source: MenuSourceEntity) = viewModelScope.launch {
        ingestor.discardStaged(source)
        _state.update { it.copy(review = null, confirmDiscard = null) }
    }

    fun open(source: MenuSourceEntity?) = _state.update { it.copy(openSourceId = source?.id) }

    fun delete(source: MenuSourceEntity) = viewModelScope.launch {
        val now = clock.nowMillis()
        ingestor.discardStaged(source)
        menuDao.softDeleteItemsOf(source.id, now)
        menuDao.softDeleteSource(source.id, now)
    }

    fun clearError() = _state.update { it.copy(error = null, refusedSource = null) }

    /**
     * The link was refused and the person saved the PDF themselves: import the picked file under the same restaurant
     * name. The refused link is removed (soft delete) because it would only be refused again.
     */
    fun replaceWithPdf(refused: MenuSourceEntity, uri: String) {
        viewModelScope.launch {
            menuDao.softDeleteItemsOf(refused.id, clock.nowMillis())
            menuDao.softDeleteSource(refused.id, clock.nowMillis())
            add(refused.restaurantName, MenuSourceKind.PDF, uri, null).join()
        }
    }
}
