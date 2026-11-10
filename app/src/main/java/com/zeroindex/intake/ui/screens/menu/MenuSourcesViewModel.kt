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
import com.zeroindex.intake.data.ai.menu.StagedItem
import com.zeroindex.intake.data.db.dao.MenuDao
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.MenuRunStatus
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.MenuSourceKind
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.domain.ai.AiTrigger
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Review(val source: MenuSourceEntity, val items: List<StagedItem>, val warnings: List<String> = emptyList())

data class MenuUiState(
    /** Where the import in flight is up to; null when none is running. */
    val running: MenuProgress? = null,
    val review: Review? = null,
    val error: String? = null,
    val hasKey: Boolean = false,
    val openSourceId: String? = null,
)

@HiltViewModel
class MenuSourcesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val menuDao: MenuDao,
    private val ingestor: MenuIngestor,
    private val gateway: AiGateway,
    private val clock: Clock,
) : ViewModel() {

    val sources: StateFlow<List<MenuSourceEntity>> =
        menuDao.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    fun run(source: MenuSourceEntity) {
        gateway.unavailableReason()?.let { reason ->
            _state.update { it.copy(error = reason.message) }
            return
        }
        if (importJob?.isActive == true) return
        _state.update { it.copy(running = MenuProgress("Starting…"), error = null) }
        importJob = viewModelScope.launch {
            menuDao.upsertSource(source.copy(lastStatus = MenuRunStatus.RUNNING, updatedAt = clock.nowMillis()))
            try {
                val outcome = ingestor.stage(source, AiTrigger.USER_ACTION) { progress ->
                    _state.update { s -> if (s.running != null) s.copy(running = progress) else s }
                }
                _state.update { it.copy(running = null, review = Review(source, outcome.items, outcome.warnings)) }
            } catch (e: CancellationException) {
                // Cancel was pressed: the source goes back to how it was before this run.
                withContext(NonCancellable) { menuDao.upsertSource(source) }
                _state.update { it.copy(running = null) }
                throw e
            } catch (e: Exception) {
                val reason = (e as? AiError)?.message ?: (e as? MenuImportException)?.message ?: "The import failed. Try again, or paste the menu text."
                ingestor.markFailed(source, reason)
                _state.update { it.copy(running = null, error = reason) }
            }
        }
    }

    /** Stops the import in flight; the OkHttp download or model call is cancelled with it. */
    fun cancelImport() {
        importJob?.cancel()
    }

    fun toggle(index: Int) = _state.update { s ->
        val r = s.review ?: return@update s
        s.copy(review = r.copy(items = r.items.mapIndexed { i, it -> if (i == index) it.copy(accepted = !it.accepted) else it }))
    }

    fun commit() {
        val r = _state.value.review ?: return
        viewModelScope.launch {
            ingestor.commit(r.source, r.items.filter { it.accepted }.map { it.entity })
            _state.update { it.copy(review = null) }
        }
    }

    fun discardReview() = viewModelScope.launch {
        _state.value.review?.let { r -> ingestor.markFailed(r.source, "Import discarded at review") }
        _state.update { it.copy(review = null) }
    }

    fun open(source: MenuSourceEntity?) = _state.update { it.copy(openSourceId = source?.id) }

    fun delete(source: MenuSourceEntity) = viewModelScope.launch {
        val now = clock.nowMillis()
        menuDao.softDeleteItemsOf(source.id, now)
        menuDao.softDeleteSource(source.id, now)
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
