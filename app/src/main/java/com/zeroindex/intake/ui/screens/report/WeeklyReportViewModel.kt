package com.zeroindex.intake.ui.screens.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.report.PdfReportRenderer
import com.zeroindex.intake.data.report.WeeklyReportRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportUiState(
    val weekStart: LocalDate = LocalDate.EPOCH,
    val isCurrentWeek: Boolean = true,
    val pages: List<Bitmap> = emptyList(),
    val file: File? = null,
    val working: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WeeklyReportRepository,
    private val renderer: PdfReportRenderer,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val thisWeek = repository.mondayOf(clock.today())
    private val _state = MutableStateFlow(ReportUiState(weekStart = thisWeek))
    val state: StateFlow<ReportUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load(thisWeek)
    }

    fun previous() = load(_state.value.weekStart.minusWeeks(1))
    fun next() { if (!_state.value.isCurrentWeek) load(_state.value.weekStart.plusWeeks(1)) }

    private fun load(weekStart: LocalDate) {
        job?.cancel()
        _state.update { it.copy(weekStart = weekStart, isCurrentWeek = weekStart == thisWeek, working = true, pages = emptyList()) }
        job = viewModelScope.launch {
            val report = repository.build(weekStart)
            val file = renderer.render(report, settings.settings.first().weightUnit)
            val pages = withContext(Dispatchers.IO) { preview(file) }
            _state.update { it.copy(pages = pages, file = file, working = false) }
        }
    }

    /** Renders the PDF back to bitmaps, so the preview is the actual file. */
    private fun preview(file: File): List<Bitmap> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                (0 until pdf.pageCount).map { i ->
                    pdf.openPage(i).use { page ->
                        Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }

    fun saveTo(uri: Uri) = viewModelScope.launch {
        val file = _state.value.file ?: return@launch
        val ok = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } }.isSuccess
        }
        _state.update { it.copy(message = if (ok) "Saved." else "Could not save the PDF there.") }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
