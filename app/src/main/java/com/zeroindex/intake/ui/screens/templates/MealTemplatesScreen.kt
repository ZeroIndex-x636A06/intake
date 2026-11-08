@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.templates

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.TagIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.repository.TemplateSummary
import com.zeroindex.intake.nfc.TagWriteResult
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.screens.today.SlotPickerSheet
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun MealTemplatesScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: MealTemplatesViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nfc = remember { NfcAdapter.getDefaultAdapter(context) }
    var logging by remember { mutableStateOf<TemplateSummary?>(null) }
    val dates = remember { DateTimeFormatter.ofPattern("d MMM") }

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
            SubHead("Tap to log", "One tap logs a meal template from a tag on its container, to whatever meal it is now.")
        }
        if (nfc == null) {
            item(key = "nonfc") {
                EmptyState(body = "This phone has no NFC. Templates still log in one tap from here.")
            }
        }
        state.message?.let { item(key = "msg") { SandNote(it) } }
        item(key = "list-head") { SectionLine("Meal templates", note = "${templates.size} saved") }
        if (templates.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "No meal templates yet",
                    body = "On Today, log a prepped meal once, then choose \"Save as meal prep\" on that meal.",
                )
            }
        } else {
            item(key = "list") {
                IntakeCard {
                    templates.forEachIndexed { i, t ->
                        TemplateRow(
                            t = t,
                            written = t.template.nfcWrittenAt?.let { dates.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) },
                            canWrite = nfc != null,
                            onLog = { logging = t },
                            onWrite = { viewModel.startWrite(t) },
                            onDelete = { viewModel.delete(t) },
                        )
                        if (i != templates.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    logging?.let { t ->
        SlotPickerSheet(
            title = "Log ${t.template.name} to",
            onDismiss = { logging = null },
            onPick = { slot -> viewModel.logNow(t, slot); logging = null },
        )
    }
    state.writingFor?.let { t -> WriteSheet(t, state.write, nfc, viewModel) }
}

@Composable
private fun TemplateRow(
    t: TemplateSummary,
    written: String?,
    canWrite: Boolean,
    onLog: () -> Unit,
    onWrite: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = "Log ${t.template.name} now", onClick = onLog)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagIcon(if (written != null) "▰" else "◌")
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(t.template.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${t.kcal.roundToInt()} kcal · ${t.protein.roundToInt()} g protein · " +
                    (written?.let { "Tag written $it" } ?: "Not on a tag"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            TextButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "More for ${t.template.name}" }) {
                Text("•••")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Log now") }, onClick = { menu = false; onLog() })
                if (canWrite) {
                    DropdownMenuItem(
                        text = { Text(if (written != null) "Write to another tag" else "Write to a tag") },
                        onClick = { menu = false; onWrite() },
                    )
                }
                DropdownMenuItem(text = { Text("Delete template") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun WriteSheet(t: TemplateSummary, write: WriteState, nfc: NfcAdapter?, viewModel: MealTemplatesViewModel) {
    val activity = LocalContext.current.findActivity()
    // Reader mode while the sheet is open: tags go to the writer, not to
    // the normal dispatch that would log the meal.
    DisposableEffect(activity, nfc) {
        if (activity != null && nfc != null) {
            nfc.enableReaderMode(
                activity,
                { tag -> viewModel.onTag(tag, activity.packageName) },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null,
            )
        }
        onDispose { if (activity != null && nfc != null) nfc.disableReaderMode(activity) }
    }
    ModalBottomSheet(onDismissRequest = viewModel::stopWrite, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Write “${t.template.name}”", style = MaterialTheme.typography.headlineSmall)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(125.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(95.dp)
                            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (write == WriteState.Waiting && nfc?.isEnabled != false) {
                            CircularProgressIndicator(Modifier.size(95.dp), strokeWidth = 2.dp)
                        }
                        Icon(Icons.Filled.Nfc, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            when (write) {
                WriteState.Waiting -> {
                    Text(
                        "Hold the back of your phone near a writable NFC tag.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (nfc?.isEnabled == false) {
                        ErrorState(body = "NFC is switched off in system settings. Turn it on, then hold the tag near the phone.")
                    }
                }
                is WriteState.Done -> {
                    when (val r = write.result) {
                        is TagWriteResult.Written -> StatusPill("Tag written · tapping it now logs ${t.template.name}")
                        TagWriteResult.ReadOnly -> ErrorState(title = "Couldn't write", body = "This tag is read-only. Use a blank, writable tag such as an NTAG213 sticker.")
                        is TagWriteResult.TooSmall -> ErrorState(title = "Couldn't write", body = "This tag holds ${r.available} bytes and this needs ${r.needed}. Try a larger NFC tag.")
                        TagWriteResult.Unsupported -> ErrorState(title = "Couldn't write", body = "This tag does not support NDEF links. Use an NTAG21x sticker.")
                        is TagWriteResult.Failed -> ErrorState(title = "Couldn't write", body = "${r.reason} Try again or use a different tag.")
                    }
                    if (write.result !is TagWriteResult.Written) {
                        Button(
                            onClick = { viewModel.startWrite(t) },
                            shape = IntakeShape.Button,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Start writing again") }
                    }
                }
            }
            TextButton(onClick = viewModel::stopWrite) { Text("Close") }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
