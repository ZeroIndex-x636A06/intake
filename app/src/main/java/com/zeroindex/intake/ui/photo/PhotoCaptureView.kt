package com.zeroindex.intake.ui.photo

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zeroindex.intake.ui.camera.CameraOwner
import com.zeroindex.intake.ui.theme.IntakeShape
import java.io.File

/**
 * SPEC §6.1: capture with CameraX, or pick from the gallery, with a framing
 * hint asking for something of known size in the shot - the single best
 * thing a user can do for the estimate.
 *
 * The gallery path needs no permission at all (the system photo picker), so a
 * user who never grants the camera can still use photo estimates.
 */
@Composable
fun PhotoCaptureView(
    newCaptureFile: () -> File,
    onPhoto: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPhoto)
    }
    val pickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }

    LaunchedEffect(Unit) { if (hasCamera && !hasPermission) permission.launch(Manifest.permission.CAMERA) }

    Box(modifier.fillMaxWidth().height(460.dp)) {
        if (!hasCamera || !hasPermission || error != null) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = when {
                        !hasCamera -> "No camera on this device"
                        !hasPermission -> "Camera access is off"
                        else -> "The camera could not start"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = error ?: "Pick a photo from your gallery instead - that needs no permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Button(onClick = pickImage, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Choose a photo")
                }
                if (hasCamera && hasPermission && error != null) {
                    OutlinedButton(
                        onClick = {
                            error = null
                            retry++
                        },
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
                    ) { Text("Try again") }
                }
                if (hasCamera && !hasPermission) {
                    OutlinedButton(
                        onClick = { permission.launch(Manifest.permission.CAMERA) },
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
                    ) { Text("Allow camera") }
                }
            }
            return@Box
        }

        val capture = remember(retry) {
            ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        }
        key(retry) { CameraSurface(capture = capture, onError = { error = it }) }

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = IntakeShape.Tile,
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
        ) {
            Text(
                text = "Include a fork, your hand or a standard plate for scale",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(onClick = pickImage, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "Choose from gallery")
            }
            FilledIconButton(
                onClick = {
                    val file = newCaptureFile()
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) =
                                onPhoto(Uri.fromFile(file))

                            override fun onError(exception: ImageCaptureException) {
                                Log.w("PhotoCapture", "Capture failed", exception)
                                error = "The photo could not be taken. Try again or choose one from the gallery."
                            }
                        },
                    )
                },
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Take photo", modifier = Modifier.size(32.dp))
            }
            Box(Modifier.size(52.dp))
        }
    }
}

/**
 * The preview and the capture use case, bound through [CameraOwner] and released when this leaves the screen. The
 * old version bound them to the screen's lifecycle and never unbound, which left the camera held after the photo
 * flow closed and made the barcode scanner fail to start (B3).
 */
@Composable
private fun CameraSurface(capture: ImageCapture, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError by rememberUpdatedState(onError)
    val view = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    DisposableEffect(lifecycleOwner, capture) {
        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        val binding = CameraOwner.bind(
            context = context,
            lifecycleOwner = lifecycleOwner,
            selector = CameraSelector.DEFAULT_BACK_CAMERA,
            useCases = listOf(preview, capture),
            onBound = {},
            onError = { currentOnError("$it Or choose a photo from the gallery instead.") },
        )
        onDispose { binding.release() }
    }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { view })
}
