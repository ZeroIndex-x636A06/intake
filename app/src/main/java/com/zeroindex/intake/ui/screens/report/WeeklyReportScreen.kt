package com.zeroindex.intake.ui.screens.report

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.zeroindex.intake.domain.report.WeekLabel
import com.zeroindex.intake.domain.report.WeeklyReport
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.ConfidenceLine
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LoadingState
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SummaryTile
import kotlin.math.roundToInt
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape
import java.io.File
import java.time.format.DateTimeFormatter

@Composable
fun WeeklyReportScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: WeeklyReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(viewModel::saveTo)
    }
    val label = WeekLabel.forWeek(state.weekStart)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") {
            SubHead("Weekly check-in", "A clear, printable snapshot · week of $label")
        }
        item(key = "weeks") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                (5 downTo 0).map { viewModel.thisWeek.minusWeeks(it.toLong()) }.forEach { week ->
                    ChoiceChip(
                        label = WeekLabel.forWeek(week),
                        selected = week == state.weekStart,
                        onClick = { viewModel.jumpTo(week) },
                    )
                }
            }
        }
        item(key = "glance") {
            IntakeCard {
                Text("Week at a glance", style = MaterialTheme.typography.titleSmall)
                val r = state.report
                if (r == null) {
                    LoadingState(listOf("Adding up the week"))
                } else {
                    Glance(r, state.weightUnit)
                }
                Button(
                    onClick = { save.launch("intake-week-${state.weekStart}.pdf") },
                    enabled = state.file != null,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                ) { Text("Save PDF") }
                OutlinedButton(
                    onClick = { state.file?.let { print(context, it) } },
                    enabled = state.file != null,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                ) { Text("Print") }
            }
        }
        state.message?.let { item(key = "msg") { SandNote(it) } }
        if (state.working && state.report != null) {
            item(key = "working") { LoadingState(listOf("Drawing the PDF preview")) }
        }
        itemsIndexed(state.pages) { i, page ->
            Surface(
                shape = IntakeShape.Tile,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    bitmap = page.asImageBitmap(),
                    contentDescription = "Report page ${i + 1} of ${state.pages.size}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(612f / 792f),
                )
            }
        }
    }
}

@Composable
private fun Glance(r: WeeklyReport, unit: WeightUnit) {
    fun n(v: Double?) = v?.let { "%,d".format(it.roundToInt()) }
    val weight = r.average { it.weightKg }?.let { "%.1f %s".format(unit.fromKg(it), unit.label) }
    val change = r.weightChangeKg?.let { "%+.1f %s".format(unit.fromKg(it), unit.label) }
    val sleep = r.average { it.sleepMinutes }?.let { "${it.toInt() / 60}h ${it.toInt() % 60}m" }
    val tiles = listOf(
        (weight ?: "—") to ("Avg weight" + (change?.let { " · $it" } ?: "")),
        (n(r.average { it.kcal })?.let { "$it kcal" } ?: "—") to "Avg intake",
        (n(r.average { it.burned })?.let { "$it kcal" } ?: "—") to "Avg burn",
        (sleep ?: "—") to "Avg sleep",
        (n(r.average { it.restingHr })?.let { "$it bpm" } ?: "—") to "Resting HR",
        (n(r.average { it.steps })) .let { it ?: "—" } to "Avg steps",
    )
    tiles.chunked(2).forEach { pair ->
        Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            pair.forEach { (value, caption) -> SummaryTile(value, caption.uppercase(), Modifier.weight(1f)) }
        }
    }
    val target = r.proteinTarget
    val logged = r.days.filter { it.protein != null }
    if (target != null && logged.isNotEmpty()) {
        val hit = logged.count { (it.protein ?: 0.0) >= target }
        ConfidenceLine(
            "Protein target hit $hit of ${r.days.size} days · ${(hit * 100.0 / r.days.size).roundToInt()}%",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (r.daysLogged == 0) {
        EmptyState(body = "Nothing logged this week. The PDF still prints whatever body and training data exists.")
    }
}

/** Hands the finished file to the system print dialog as-is. */
private fun print(context: Context, file: File) {
    val manager = context.getSystemService(PrintManager::class.java) ?: return
    manager.print(
        file.nameWithoutExtension,
        object : PrintDocumentAdapter() {
            override fun onLayout(
                old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?,
                callback: LayoutResultCallback, extras: Bundle?,
            ) {
                if (cancel?.isCanceled == true) return callback.onLayoutCancelled()
                callback.onLayoutFinished(
                    PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
                    true,
                )
            }

            override fun onWrite(
                pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                runCatching {
                    file.inputStream().use { input ->
                        ParcelFileDescriptor.AutoCloseOutputStream(destination).use { input.copyTo(it) }
                    }
                }.onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                    .onFailure { callback.onWriteFailed(it.message) }
            }
        },
        PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.NA_LETTER).build(),
    )
}
