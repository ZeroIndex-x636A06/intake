package com.zeroindex.intake.ui.screens.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.backup.BackupService
import com.zeroindex.intake.data.backup.ImportException
import com.zeroindex.intake.data.backup.ImportPreview
import com.zeroindex.intake.data.backup.WrongPassphraseException
import com.zeroindex.intake.data.photos.ProgressPhotoRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportStage {
    data object Idle : ImportStage
    data class NeedsPassphrase(val bytes: ByteArray, val wrong: Boolean = false) : ImportStage
    data class Review(val preview: ImportPreview) : ImportStage
}

/**
 * Local export and import (Settings > Sync and export): everything as one JSON file, optionally encrypted with a
 * passphrase typed for that file, three CSV spreadsheets, and an optional zip of the progress photos. Old `.intake`
 * files from the removed remote backups still import: the decrypt path in [BackupService] is unchanged.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backupService: BackupService,
    private val photos: ProgressPhotoRepository,
    private val clock: Clock,
) : ViewModel() {

    val lastExportAt: StateFlow<Long?> = settingsRepository.settings.map { it.lastExportAtMillis }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val includePhotos: StateFlow<Boolean> = settingsRepository.settings.map { it.exportIncludePhotos }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val message = MutableStateFlow<String?>(null)
    val import = MutableStateFlow<ImportStage>(ImportStage.Idle)

    fun setIncludePhotos(on: Boolean) { viewModelScope.launch { settingsRepository.setExportIncludePhotos(on) } }

    fun exportTo(uri: Uri, passphrase: String) = viewModelScope.launch {
        val ok = runCatching {
            val bytes = backupService.exportBytes(passphrase.takeIf { it.isNotEmpty() }?.toCharArray())
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream") }
            settingsRepository.setLastExportAt(clock.nowMillis())
        }.isSuccess
        message.value = if (ok) {
            "Exported." + if (passphrase.isEmpty()) " The file is not encrypted: keep it somewhere private." else ""
        } else {
            "Could not write the export there."
        }
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

    /** The photo files as a zip. The JSON export carries photo metadata only; this is the opt-in file carrier. */
    fun photosTo(uri: Uri) = viewModelScope.launch {
        val count = runCatching {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { photos.writeZip(it) } ?: error("no stream") }
        }.getOrNull()
        message.value = if (count == null) "Could not write the photos there." else "Saved $count photo${if (count == 1) "" else "s"} to a zip."
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
