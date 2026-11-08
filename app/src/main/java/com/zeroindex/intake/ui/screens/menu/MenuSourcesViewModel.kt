package com.zeroindex.intake.ui.screens.menu

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.menu.MenuIngestor
import com.zeroindex.intake.data.ai.menu.StagedItem
import com.zeroindex.intake.data.db.dao.MenuDao
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.MenuRunStatus
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.MenuSourceKind
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Review(val source: MenuSourceEntity, val items: List<StagedItem>)

data class MenuUiState(
    val running: String? = null,
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
    private val keys: ApiKeyStore,
    private val clock: Clock,
) : ViewModel() {

    val sources: StateFlow<List<MenuSourceEntity>> =
        menuDao.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(MenuUiState(hasKey = keys.openRouterKey() != null))
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

    fun run(source: MenuSourceEntity) {
        if (keys.openRouterKey() == null) {
            _state.update { it.copy(error = AiError.NoKey.message) }
            return
        }
        _state.update { it.copy(running = "Reading ${source.restaurantName}'s menu…", error = null) }
        viewModelScope.launch {
            menuDao.upsertSource(source.copy(lastStatus = MenuRunStatus.RUNNING, updatedAt = clock.nowMillis()))
            runCatching { ingestor.stage(source) }
                .onSuccess { items -> _state.update { it.copy(running = null, review = Review(source, items)) } }
                .onFailure { e ->
                    val reason = (e as? AiError)?.message ?: e.message ?: "Import failed."
                    ingestor.markFailed(source, reason)
                    _state.update { it.copy(running = null, error = reason) }
                }
        }
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
