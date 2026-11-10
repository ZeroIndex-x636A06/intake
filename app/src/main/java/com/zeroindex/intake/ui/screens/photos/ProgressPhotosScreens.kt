@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.screens.photos

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.BodyMetricEntity
import com.zeroindex.intake.data.photos.ProgressPhotoRepository
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.photos.CompareModel
import com.zeroindex.intake.domain.photos.PhotoAngle
import com.zeroindex.intake.domain.photos.PhotoCompare
import com.zeroindex.intake.domain.photos.PhotoDay
import com.zeroindex.intake.domain.photos.PhotoMeta
import com.zeroindex.intake.domain.photos.PhotoTimeline
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotosUiState(
    val days: List<PhotoDay> = emptyList(),
    val metrics: List<BodyMetricEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val newestAt: Long? = null,
)

@HiltViewModel
class ProgressPhotosViewModel @Inject constructor(
    private val repository: ProgressPhotoRepository,
    body: BodyRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<PhotosUiState> = combine(repository.observe(), body.observeAll(), settings.settings) { photos, metrics, s ->
        PhotosUiState(PhotoTimeline.group(photos, clock.zone()), metrics, s, photos.maxOfOrNull { it.takenAt })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotosUiState())

    val message = MutableStateFlow<String?>(null)

    fun fileOf(meta: PhotoMeta): File? = repository.fileOf(meta)
    fun captureFile(): File = repository.newCaptureFile()
    fun zone(): ZoneId = clock.zone()
    fun today(): LocalDate = clock.today()

    fun add(uri: Uri, angle: PhotoAngle, date: LocalDate) {
        viewModelScope.launch {
            // Midday on the chosen day, so the photo lands on that calendar date in any time zone.
            val millis = date.atTime(12, 0).atZone(clock.zone()).toInstant().toEpochMilli()
            val ok = repository.add(uri, angle, millis)
            message.value = if (ok) "Photo saved on this phone." else "That file could not be read as an image."
        }
    }

    fun hide(meta: PhotoMeta) { viewModelScope.launch { repository.hide(meta.id) } }
    fun clearMessage() { message.value = null }
}

/** A progress photo decoded off the main thread at about [targetPx] wide; a placeholder box when the file is missing. */
@Composable
fun PhotoImage(file: File?, modifier: Modifier = Modifier, targetPx: Int = 720, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<Bitmap?>(null, file) {
        value = if (file == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = "Progress photo", contentScale = contentScale, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(if (file == null) "On another device" else "…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("MMM d")
private val dayFormatYear = DateTimeFormatter.ofPattern("MMM d, yyyy")
internal fun dayLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFormatYear)
private fun shortLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFormat)

/**
 * You > Progress photos: add a photo from the camera or the gallery for one angle and a date, then the timeline grid.
 * Photos are stored on this phone only, never synced or uploaded; they go into an export only if the opt-in switch in
 * Settings > Sync and export is on.
 */
@Composable
fun ProgressPhotosScreen(
    contentPadding: PaddingValues,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgressPhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var angle by remember { mutableStateOf(PhotoAngle.FRONT) }
    var date by remember { mutableStateOf(viewModel.today()) }
    var pickingDate by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoDay?>(null) }
    var pendingCapture by remember { mutableStateOf<File?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.add(uri, angle, date) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCapture
        if (ok && file != null) viewModel.add(Uri.fromFile(file), angle, date)
    }
    fun takePhoto() {
        val file = viewModel.captureFile().also { pendingCapture = it }
        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) takePhoto() }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "add") {
            IntakeCard {
                Text("Add a photo", style = MaterialTheme.typography.titleSmall)
                Text("Every angle is optional.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    PhotoAngle.entries.forEach { a -> ChoiceChip(a.label, a == angle, { angle = a }) }
                    ChoiceChip("Date: ${date.format(dayFormatYear)}", false, { pickingDate = true })
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
                            else permission.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Camera") }
                    OutlinedButton(
                        onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Gallery") }
                }
                message?.let {
                    SandNote(it)
                    TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                }
            }
        }
        item(key = "compare") {
            OutlinedButton(onClick = onCompare, enabled = state.days.size >= 2, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (state.days.size >= 2) "Compare two dates" else "Add photos on two dates to compare")
            }
        }
        item(key = "timeline-head") { SectionLine("Timeline", note = "${state.days.size} date${if (state.days.size == 1) "" else "s"}") }
        if (state.days.isEmpty()) item(key = "empty") { SandNote("No photos yet. Photos are stored on this phone only.") }
        item(key = "grid") {
            // A fixed-height grid inside the list: the number of dates is small, so measuring all of it is cheap.
            val rows = (state.days.size + 2) / 3
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(max = (rows * 190).dp),
            ) {
                items(state.days, key = { it.epochDay }) { d ->
                    Box(Modifier.aspectRatio(0.72f).clickable { viewing = d }) {
                        PhotoImage(d.cover?.let(viewModel::fileOf), Modifier.fillMaxSize(), targetPx = 400)
                        Text(
                            shortLabel(d.epochDay) + if (d.angles.size > 1) " · ${d.angles.size}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        item(key = "footer") {
            Text(
                "Stored on this phone only. Not synced. Included in an export only if you turn that on in Settings > Sync and export.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().coerceAtMost(viewModel.today()) }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
    viewing?.let { d ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(dayLabel(d.epochDay)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.photos.values.forEach { p ->
                        Text(p.angle.label, style = MaterialTheme.typography.labelMedium)
                        PhotoImage(viewModel.fileOf(p), Modifier.fillMaxWidth().aspectRatio(0.75f), targetPx = 900)
                        TextButton(onClick = { viewModel.hide(p); viewing = null }) { Text("Hide this photo") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } },
        )
    }
}

private fun LocalDate.coerceAtMost(max: LocalDate): LocalDate = if (isAfter(max)) max else this

/**
 * You > Progress photos > Compare: pick two dates, see the same angle side by side with a draggable divider (the older
 * photo on the left of the line, the newer on the right), and the weight and waist on those dates underneath.
 */
@Composable
fun PhotoCompareScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier, viewModel: ProgressPhotosViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val days = state.days
    var firstDay by remember(days.size) { mutableStateOf(days.lastOrNull()?.epochDay) }
    var secondDay by remember(days.size) { mutableStateOf(days.firstOrNull()?.epochDay) }
    var angle by remember { mutableStateOf<PhotoAngle?>(null) }
    var divider by remember { mutableFloatStateOf(0.5f) }
    val a = days.firstOrNull { it.epochDay == firstDay }
    val b = days.firstOrNull { it.epochDay == secondDay }
    val model = if (a != null && b != null) PhotoCompare.build(a, b, angle) else null

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (days.size < 2) {
            item(key = "few") { SandNote("Add photos on two different dates to compare them.") }
            return@LazyColumn
        }
        item(key = "pick1") { DateChips("Date one", days, firstDay) { firstDay = it } }
        item(key = "pick2") { DateChips("Date two", days, secondDay) { secondDay = it } }
        if (model == null) {
            item(key = "none") { SandNote("Those two dates have no angle in common, or are the same day. Pick other dates.") }
        } else {
            val common = PhotoCompare.commonAngles(model.older.day, model.newer.day)
            if (common.size > 1) item(key = "angle") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { common.forEach { c -> ChoiceChip(c.label, c == model.angle, { angle = c }) } }
            }
            item(key = "slider") { CompareSlider(model, viewModel, divider) { divider = it } }
            item(key = "span") {
                Text("${PhotoCompare.spanLabel(model)} · drag the line", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "numbers") {
                IntakeCard {
                    Text("Weight and body on those dates", style = MaterialTheme.typography.titleSmall)
                    listOf(model.older, model.newer).forEach { side ->
                        val m = nearest(state.metrics, side.day.epochDay, viewModel.zone())
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(dayLabel(side.day.epochDay), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (m == null) "no weigh-in within a week" else
                                    state.settings.weightUnit.format(m.weightKg) + (m.waistCm?.let { " · waist ${state.settings.lengthUnit.format(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The weigh-in nearest to a photo date, within a week. */
private fun nearest(metrics: List<BodyMetricEntity>, epochDay: Long, zone: ZoneId): BodyMetricEntity? =
    metrics.minByOrNull { abs(Instant.ofEpochMilli(it.measuredAt).atZone(zone).toLocalDate().toEpochDay() - epochDay) }
        ?.takeIf { abs(Instant.ofEpochMilli(it.measuredAt).atZone(zone).toLocalDate().toEpochDay() - epochDay) <= 7 }

@Composable
private fun DateChips(label: String, days: List<PhotoDay>, selected: Long?, onPick: (Long) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { d -> ChoiceChip(shortLabel(d.epochDay), d.epochDay == selected, { onPick(d.epochDay) }) }
        }
    }
}

/** Two photos in one frame: the older one shows left of the divider, the newer right of it. */
@Composable
private fun CompareSlider(model: CompareModel, viewModel: ProgressPhotosViewModel, divider: Float, onDivider: (Float) -> Unit) {
    var widthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clipToBounds()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(model) {
                detectHorizontalDragGestures { change, delta ->
                    change.consume()
                    onDivider(PhotoCompare.dragDivider(divider, delta, widthPx))
                }
            },
    ) {
        PhotoImage(viewModel.fileOf(model.newer.photo), Modifier.fillMaxSize(), targetPx = 900)
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(left = 0f, top = 0f, right = size.width * divider, bottom = size.height, clipOp = ClipOp.Intersect) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) { PhotoImage(viewModel.fileOf(model.older.photo), Modifier.fillMaxSize(), targetPx = 900) }
        Text(shortLabel(model.older.day.epochDay), color = Color.White, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.45f)).padding(6.dp))
        Text(shortLabel(model.newer.day.epochDay), color = Color.White, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.45f)).padding(6.dp))
        // The divider line and its handle.
        Box(
            Modifier
                .offset { IntOffset((widthPx * divider).toInt() - with(density) { 1.dp.roundToPx() }, 0) }
                .fillMaxSize()
                .padding(0.dp),
        ) {
            Box(Modifier.size(width = 2.dp, height = 10000.dp).background(Color.White))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = (-17).dp)
                    .size(36.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.DragHandle, contentDescription = "Drag to compare", tint = Color.Black) }
        }
    }
}
