package com.zeroindex.intake.ui.screens.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.backup.BackupService
import com.zeroindex.intake.data.backup.ImportException
import com.zeroindex.intake.data.backup.ImportPreview
import com.zeroindex.intake.data.backup.WrongPassphraseException
import com.zeroindex.intake.data.backup.remote.BackupCredentialStore
import com.zeroindex.intake.data.backup.remote.BackupOutcome
import com.zeroindex.intake.data.backup.remote.BackupRunner
import com.zeroindex.intake.data.backup.remote.BackupSecretsState
import com.zeroindex.intake.data.backup.remote.ConnectionTest
import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.entity.BackupRunEntity
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.backup.BackupDestination
import com.zeroindex.intake.domain.backup.BackupProtocol
import com.zeroindex.intake.domain.backup.BackupSchedule
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What is typed into the server form, before it is saved. Secrets blank = keep the saved one. */
data class DestinationDraft(
    val destination: BackupDestination = BackupDestination(),
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
)

sealed interface ImportStage {
    data object Idle : ImportStage
    data class NeedsPassphrase(val bytes: ByteArray, val wrong: Boolean = false) : ImportStage
    data class Review(val preview: ImportPreview) : ImportStage
}

data class BackupUiState(
    val settings: AppSettings = AppSettings(),
    val secrets: BackupSecretsState = BackupSecretsState(),
    val runs: List<BackupRunEntity> = emptyList(),
    val now: Long = 0,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val credentials: BackupCredentialStore,
    private val runner: BackupRunner,
    private val backupService: BackupService,
    dao: HealthSummaryDao,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<BackupUiState> = combine(
        settingsRepository.settings,
        credentials.state,
        dao.observeBackupRuns(limit = 5),
    ) { settings, secrets, runs -> BackupUiState(settings, secrets, runs, clock.nowMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    val draft = MutableStateFlow(DestinationDraft())
    val test = MutableStateFlow<ConnectionTest?>(null)
    val testing = MutableStateFlow(false)
    val backingUp = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val import = MutableStateFlow<ImportStage>(ImportStage.Idle)

    init {
        viewModelScope.launch {
            settingsRepository.settings.first().backupDestination?.let { saved -> draft.update { it.copy(destination = saved) } }
        }
    }

    fun edit(block: (DestinationDraft) -> DestinationDraft) {
        draft.update(block)
        test.value = null
    }

    fun setProtocol(protocol: BackupProtocol) = edit {
        it.copy(destination = it.destination.copy(protocol = protocol, hostKeySha256 = null), password = "")
    }

    private suspend fun saveDraft(): BackupDestination {
        val d = draft.value
        if (d.password.isNotEmpty()) credentials.setPassword(d.destination.protocol, d.password)
        if (d.privateKey.isNotBlank()) credentials.setSftpPrivateKey(d.privateKey)
        if (d.passphrase.isNotEmpty()) credentials.setPassphrase(d.passphrase)
        settingsRepository.setBackupDestination(d.destination)
        draft.update { it.copy(password = "", privateKey = "", passphrase = "") }
        return d.destination
    }

    fun saveAndTest() = viewModelScope.launch {
        testing.value = true
        test.value = runner.test(saveDraft())
        testing.value = false
    }

    /** Pins the fingerprint the user just looked at, then tests again with it. */
    fun trustHostKey(fingerprint: String) = viewModelScope.launch {
        edit { it.copy(destination = it.destination.copy(hostKeySha256 = fingerprint)) }
        saveAndTest()
    }

    fun setSchedule(schedule: BackupSchedule) = viewModelScope.launch { settingsRepository.setBackupSchedule(schedule) }

    fun forgetServer() = viewModelScope.launch {
        BackupProtocol.entries.forEach { credentials.setPassword(it, null) }
        credentials.setSftpPrivateKey(null)
        settingsRepository.setBackupDestination(null)
        draft.value = DestinationDraft()
        test.value = null
    }

    fun backUpNow() = viewModelScope.launch {
        backingUp.value = true
        saveDraft()
        message.value = when (val o = runner.run()) {
            is BackupOutcome.Done -> "Backed up ${o.bytes / 1024} KB as ${o.name}" + if (o.pruned > 0) ". Removed ${o.pruned} old backups." else "."
            is BackupOutcome.Skipped -> o.reason
            is BackupOutcome.Failed -> null // shown from the recorded run
        }
        backingUp.value = false
    }

    // ---- file export / import (SPEC §13) ----

    fun exportTo(uri: Uri, passphrase: String) = viewModelScope.launch {
        val ok = runCatching {
            val bytes = backupService.exportBytes(passphrase.takeIf { it.isNotEmpty() }?.toCharArray())
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream") }
            settingsRepository.setLastExportAt(clock.nowMillis())
        }.isSuccess
        message.value = if (ok) "Exported." + if (passphrase.isEmpty()) " The file is not encrypted - keep it somewhere private." else "" else
            "Could not write the export there."
    }

    fun csvTo(uri: Uri, kind: CsvKind) = viewModelScope.launch {
        val ok = runCatching {
            val bytes = when (kind) {
                CsvKind.FOOD -> backupService.foodLogCsv()
                CsvKind.WORKOUTS -> backupService.workoutCsv()
                CsvKind.BODY -> backupService.bodyMetricCsv()
            }
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream") }
        }.isSuccess
        message.value = if (ok) "${kind.label} CSV saved." else "Could not write the CSV there."
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
        if (bytes == null) {
            message.value = "Could not read that file."
            return@launch
        }
        if (backupService.needsPassphrase(bytes)) import.value = ImportStage.NeedsPassphrase(bytes) else preview(bytes, null)
    }

    fun unlockImport(passphrase: String) {
        val stage = import.value as? ImportStage.NeedsPassphrase ?: return
        viewModelScope.launch { preview(stage.bytes, passphrase.toCharArray()) }
    }

    private suspend fun preview(bytes: ByteArray, passphrase: CharArray?) {
        try {
            import.value = ImportStage.Review(backupService.preview(bytes, passphrase))
        } catch (e: WrongPassphraseException) {
            import.value = ImportStage.NeedsPassphrase(bytes, wrong = true)
        } catch (e: ImportException) {
            import.value = ImportStage.Idle
            message.value = e.message
        } finally {
            passphrase?.fill('\u0000')
        }
    }

    fun applyImport() = viewModelScope.launch {
        val stage = import.value as? ImportStage.Review ?: return@launch
        message.value = runCatching { backupService.apply(stage.preview) }
            .fold({ "Imported ${stage.preview.totalChanges} changes." }, { "Import failed; nothing was changed." })
        import.value = ImportStage.Idle
    }

    fun cancelImport() { import.value = ImportStage.Idle }
    fun clearMessage() { message.value = null }
}

enum class CsvKind(val label: String, val fileStem: String) {
    FOOD("Food log", "food-log"),
    WORKOUTS("Workouts", "workouts"),
    BODY("Body metrics", "body-metrics"),
}
