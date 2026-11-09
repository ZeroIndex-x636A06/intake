@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.screens.train

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.ExercisePhotoPair
import com.zeroindex.intake.ui.components.MainAndAlsoMusclePicker
import com.zeroindex.intake.ui.theme.IntakeShape
import java.io.File
import kotlinx.coroutines.launch

/** What the custom-exercise form collects. */
data class ExerciseForm(
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val equipment: String,
    val isBodyweight: Boolean,
    val defaultRestSec: Int,
    val howTo: String?,
    /** File name of the person's own photo in app storage, or null. */
    val photoPath: String?,
)

/**
 * Add your own exercise, or edit one: a name, an optional photo from the camera or gallery, the muscles it
 * works (tap the body or pick from the list), the equipment, rest and optional how-to text.
 */
@Composable
fun ExerciseFormSheet(
    existing: ExerciseEntity?,
    viewModel: ExerciseLibraryViewModel,
    onDismiss: () -> Unit,
    onSave: (ExerciseForm) -> Unit,
    initialName: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: initialName) }
    var primary by remember { mutableStateOf(existing?.primaryMuscle?.takeIf { it.isNotBlank() }) }
    var secondary by remember {
        mutableStateOf(existing?.secondaryMuscles.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }
    var equipment by remember { mutableStateOf(existing?.equipment.orEmpty()) }
    var bodyweight by remember { mutableStateOf(existing?.isBodyweight ?: false) }
    var rest by remember { mutableStateOf((existing?.defaultRestSec ?: 90).toString()) }
    var howTo by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var photo by remember { mutableStateOf(existing?.photoPath) }
    var photoError by remember { mutableStateOf(false) }
    var pendingCapture by remember { mutableStateOf<File?>(null) }

    fun importFrom(uri: Uri) {
        scope.launch {
            val saved = viewModel.importPhoto(uri)
            if (saved != null) { photo = saved; photoError = false } else photoError = true
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) importFrom(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCapture
        if (ok && file != null) importFrom(Uri.fromFile(file))
    }
    fun takePhoto() {
        val file = viewModel.captureFile().also { pendingCapture = it }
        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePhoto() else photoError = true
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (existing == null) "Add your own exercise" else "Edit exercise", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Eyebrow("Photo (optional)")
            if (photo != null) ExercisePhotoPair(existing?.id ?: "new-exercise", photo, sideBySide = false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            takePhoto()
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    shape = IntakeShape.Button,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Take a photo") }
                OutlinedButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = IntakeShape.Button,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Choose from gallery") }
                if (photo != null) TextButton(onClick = { photo = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove photo") }
            }
            if (photoError) {
                Text(
                    "That photo could not be used. Try another, or allow the camera in Android settings.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "Your photo stays on this phone. It is not in backups or sync, so it will not come back after a restore or on another device.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Eyebrow("Muscles")
            MainAndAlsoMusclePicker(
                primary = primary,
                secondary = secondary,
                onPrimary = { m -> primary = m; secondary = secondary - m },
                onToggleSecondary = { m -> secondary = if (m in secondary) secondary - m else secondary + m },
            )

            Eyebrow("Equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Equipment.ALL.forEach { e ->
                    ChoiceChip(Equipment.label(e), selected = equipment == e, onClick = { equipment = if (equipment == e) "" else e })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Bodyweight exercise", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = bodyweight, onCheckedChange = { bodyweight = it })
            }
            OutlinedTextField(
                value = rest,
                onValueChange = { rest = it.filter(Char::isDigit).take(3) },
                label = { Text("Usual rest between sets (seconds)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = howTo,
                onValueChange = { howTo = it.take(1_000) },
                label = { Text("How to (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSave(
                        ExerciseForm(
                            name = name.trim(),
                            primaryMuscle = primary.orEmpty(),
                            secondaryMuscles = secondary.filter { it != primary }.sorted(),
                            equipment = equipment,
                            isBodyweight = bodyweight,
                            defaultRestSec = (rest.toIntOrNull() ?: 90).coerceIn(15, 600),
                            howTo = howTo.trim().ifBlank { null },
                            photoPath = photo,
                        ),
                    )
                },
                enabled = name.isNotBlank() && primary != null,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Save") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
