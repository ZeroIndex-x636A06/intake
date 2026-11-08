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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.repository.TemplateSummary
import com.zeroindex.intake.nfc.TagWriteResult
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.Tag
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
            SubHead("Meal prep tags", "Stick an NFC tag on a container; tapping it logs the meal to whatever meal it is now.")
        }
        if (nfc == null) {
            item(key = "nonfc") {
                AccentNote(
                    lead = "This phone has no NFC",
                    body = "Templates still log in one tap from here.",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.message?.let { item(key = "msg") { AccentNote(lead = it, body = "") } }
        if (templates.isEmpty()) {
            item(key = "empty") {
                AccentNote(
                    lead = "No meal prep saved yet",
                    body = "On Today, log a prepped meal once, then tap \"Save as meal prep\" on that meal.",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(templates, key = { it.template.id }) { t ->
            IntakeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.template.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${t.items.size} foods · ${t.kcal.roundToInt()} kcal · ${t.protein.roundToInt()} g protein",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (t.template.nfcTagUid != null) {
                        Icon(Icons.Filled.Nfc, contentDescription = "Written to a tag", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                t.template.nfcWrittenAt?.let {
                    Spacer(Modifier.height(4.dp))
                    Tag("On a tag since ${dates.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}")
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { logging = t }, shape = IntakeShape.Chip) { Text("Log now") }
                    if (nfc != null) {
                        OutlinedButton(onClick = { viewModel.startWrite(t) }, shape = IntakeShape.Chip) { Text("Write to tag") }
                    }
                    TextButton(onClick = { viewModel.delete(t) }) { Text("Delete") }
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
            Text("Write \"${t.template.name}\"", style = MaterialTheme.typography.headlineSmall)
            when (write) {
                WriteState.Waiting -> {
                    Icon(Icons.Filled.Nfc, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Hold the tag against the back of the phone", style = MaterialTheme.typography.bodyMedium)
                    if (nfc?.isEnabled == false) {
                        Text("NFC is switched off in system settings.", color = MaterialTheme.colorScheme.error)
                    } else {
                        CircularProgressIndicator()
                    }
                }
                is WriteState.Done -> {
                    val (title, body) = when (val r = write.result) {
                        is TagWriteResult.Written -> "Done" to "Tapping this tag now logs ${t.template.name}."
                        TagWriteResult.ReadOnly -> "That tag is read-only" to "Use a blank, writable tag such as an NTAG213 sticker."
                        is TagWriteResult.TooSmall -> "That tag is too small" to "It holds ${r.available} bytes; this needs ${r.needed}."
                        TagWriteResult.Unsupported -> "That tag can't hold a link" to "It does not support NDEF. Use an NTAG21x sticker."
                        is TagWriteResult.Failed -> "Writing failed" to r.reason
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodySmall)
                    if (write.result !is TagWriteResult.Written) {
                        OutlinedButton(onClick = { viewModel.startWrite(t) }, shape = IntakeShape.Button) { Text("Try another tag") }
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
