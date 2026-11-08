package com.zeroindex.intake.ui.barcode

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * SPEC §5.2: CameraX preview plus ML Kit barcode scanning, entirely on-device.
 *
 * Every failure path lands somewhere useful rather than on a blank screen: no
 * permission, no camera hardware, or a camera that refuses to bind all fall
 * back to entering the food by hand.
 */
@Composable
fun BarcodeScannerView(
    onBarcode: (String) -> Unit,
    onEnterManually: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !hasCamera -> FallbackMessage(
                title = "No camera on this device",
                body = "You can still add the product by hand.",
                onEnterManually = onEnterManually,
            )

            !hasPermission -> FallbackMessage(
                title = "Camera access is off",
                body = "Scanning needs the camera. You can grant it, or just add the product " +
                    "by hand - nothing else in the app depends on it.",
                onEnterManually = onEnterManually,
                onGrant = { launcher.launch(Manifest.permission.CAMERA) },
            )

            cameraError != null -> FallbackMessage(
                title = "The camera could not start",
                body = cameraError.orEmpty(),
                onEnterManually = onEnterManually,
            )

            else -> {
                CameraPreview(onBarcode = onBarcode, onError = { cameraError = it })
                // SPEC §5.2 framing help, kept out of the centre of the frame.
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Point the camera at the barcode",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                        OutlinedButton(
                            onClick = onEnterManually,
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
                        ) { Text("Enter it by hand instead", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onBarcode: (String) -> Unit, onError: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                )
                .build(),
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, BarcodeAnalyzer(scanner, onBarcode)) }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (e: Exception) {
                    Log.w("BarcodeScanner", "Camera bind failed", e)
                    onError(
                        "Something else may be using the camera. Try again, or add the " +
                            "product by hand.",
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

/** Reports the first barcode it sees, once. */
private class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile private var delivered = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (delivered) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                if (value != null && !delivered) {
                    delivered = true
                    onBarcode(value)
                }
            }
            .addOnFailureListener { Log.w("BarcodeScanner", "Barcode scan failed", it) }
            .addOnCompleteListener { imageProxy.close() }
    }
}

@Composable
private fun FallbackMessage(
    title: String,
    body: String,
    onEnterManually: () -> Unit,
    onGrant: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        if (onGrant != null) {
            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Allow camera") }
        }
        OutlinedButton(
            onClick = onEnterManually,
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
        ) { Text("Add it by hand") }
    }
}
