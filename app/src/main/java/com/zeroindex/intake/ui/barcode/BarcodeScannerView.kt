package com.zeroindex.intake.ui.barcode

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.zeroindex.intake.domain.barcode.Barcodes
import com.zeroindex.intake.domain.barcode.ScannedRead
import java.util.concurrent.Executors

private const val TAG = "BarcodeScanner"

/**
 * SPEC §5.2: CameraX preview plus ML Kit barcode scanning, entirely on device.
 *
 * Everything the user can hit has a state of its own rather than a blank screen: no
 * camera hardware, permission not yet asked, permission denied (with a route to the
 * system settings once Android stops asking), and a camera that refuses to start. In
 * every one of them the barcode can still be typed, and a product can still be
 * added by hand.
 *
 * [onBarcode] is called with a canonical, check-digit-valid barcode (see [Barcodes]),
 * whether it came from the camera or the keyboard.
 */
@Composable
fun BarcodeScannerView(
    onBarcode: (String) -> Unit,
    onEnterManually: () -> Unit,
    modifier: Modifier = Modifier,
    /** Leaves the scanner (back to the sheet's other tabs). System back and the Back button both call it. */
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // System back always leaves the scanner, whatever state it is in.
    BackHandler(onBack = onExit)

    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var blockedForGood by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        // After a refusal Android may stop showing the dialog; then only Settings can grant it.
        blockedForGood = !granted && context.findActivity()?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !askedOnce) {
            askedOnce = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // The user may have granted the permission in system Settings and come back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = context.hasCameraPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    LaunchedEffect(torchOn, camera) {
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(torchOn)
    }

    // The camera lives in a bounded 4:3 card, clipped to it, so the preview can never grow over the sheet's own
    // tabs, Close button or the manual entry below. The controls sit above the preview in z-order (the
    // preview is a TextureView, which would otherwise draw over siblings that come earlier).
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .clipToBounds(),
        ) {
            when {
                !hasCamera -> FallbackMessage(
                    title = "No camera on this device",
                    body = "You can type the barcode number below, or add the product by hand.",
                    onEnterManually = onEnterManually,
                )

                !hasPermission -> FallbackMessage(
                    title = "Camera access is off",
                    body = "Scanning needs the camera, and the picture never leaves your phone. " +
                        "You can allow it, type the barcode number below, or add the product by hand.",
                    onEnterManually = onEnterManually,
                    primaryLabel = if (blockedForGood) "Open app settings" else "Allow camera",
                    onPrimary = {
                        if (blockedForGood) context.openAppSettings() else launcher.launch(Manifest.permission.CAMERA)
                    },
                )

                cameraError != null -> FallbackMessage(
                    title = "The camera could not start",
                    body = cameraError.orEmpty(),
                    onEnterManually = onEnterManually,
                    primaryLabel = "Try again",
                    onPrimary = {
                        cameraError = null
                        retry++
                    },
                )

                else -> {
                    key(retry) {
                        CameraPreview(
                            onBarcode = onBarcode,
                            onCamera = { camera = it },
                            onError = { cameraError = it },
                        )
                    }
                    // A frame to aim at, kept clear of the centre.
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.78f)
                            .height(120.dp),
                    ) {
                        Surface(
                            color = Color.Transparent,
                            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {}
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    ) {
                        Text(
                            text = "Point the camera at the barcode",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    if (camera?.cameraInfo?.hasFlashUnit() == true) {
                        IconButton(
                            onClick = { torchOn = !torchOn },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp).zIndex(2f),
                        ) {
                            Icon(
                                imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = if (torchOn) "Turn the torch off" else "Turn the torch on",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
            // Back is always drawn above the preview, in every state.
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(48.dp).zIndex(2f),
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave the scanner", tint = Color.White)
                }
            }
        }
        ManualBarcodeEntry(onBarcode = onBarcode)
    }
}

/**
 * The fallback that always works: type the number under the bars. It goes through the
 * same check-digit test as a scan, so a mistyped digit is caught here, with a message,
 * instead of becoming a lookup that cannot succeed.
 */
@Composable
private fun ManualBarcodeEntry(onBarcode: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val code = Barcodes.normalize(text)
        if (code == null) {
            error = "That is not a valid barcode. Check the digits under the bars " +
                "(8, 12 or 13 numbers)."
        } else {
            error = null
            onBarcode(code)
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { next ->
                    text = next.filter(Char::isDigit).take(14)
                    error = null
                },
                label = { Text("Or type the barcode number") },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = ::submit,
                enabled = text.length >= 8,
                modifier = Modifier.height(56.dp),
            ) { Text("Look up") }
        }
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CameraPreview(
    onBarcode: (String) -> Unit,
    onCamera: (Camera?) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnCamera by rememberUpdatedState(onCamera)
    val currentOnError by rememberUpdatedState(onError)

    // COMPATIBLE renders through a TextureView. The default (a SurfaceView) can draw
    // behind or not at all inside the dialog window a bottom sheet lives in.
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                )
                .build(),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        var stateObserver: Observer<androidx.camera.core.CameraState>? = null
        var boundCamera: Camera? = null

        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider
                val newPreview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                // The 640x480 default is too coarse for the narrow bars of a small EAN
                // symbol; 1280x720 reads them reliably.
                val newAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, BarcodeAnalyzer(scanner) { code -> currentOnBarcode(code) }) }

                cameraProvider.unbind(newPreview, newAnalysis)
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    newPreview,
                    newAnalysis,
                )
                preview = newPreview
                analysis = newAnalysis
                boundCamera = camera

                val observer = Observer<androidx.camera.core.CameraState> { state ->
                    if (state.error != null) {
                        Log.w(TAG, "Camera error ${state.error?.code}")
                        currentOnError(
                            "Another app may be using the camera. Close it and try again, or " +
                                "type the barcode number instead.",
                        )
                    }
                }
                stateObserver = observer
                camera.cameraInfo.cameraState.observe(lifecycleOwner, observer)
                currentOnCamera(camera)
            } catch (e: Exception) {
                Log.w(TAG, "Camera bind failed", e)
                currentOnError(
                    "Something else may be using the camera. Try again, or type the barcode " +
                        "number instead.",
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            // The camera is bound to the screen's lifecycle owner, not to this composable,
            // so it must be released here or it keeps running behind a closed sheet.
            disposed = true
            currentOnCamera(null)
            stateObserver?.let { observer -> boundCamera?.cameraInfo?.cameraState?.removeObserver(observer) }
            analysis?.clearAnalyzer()
            val used = listOfNotNull(preview, analysis).toTypedArray()
            if (used.isNotEmpty()) runCatching { provider?.unbind(*used) }
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    AndroidView(modifier = Modifier.fillMaxSize().zIndex(0f), factory = { previewView })
}

/**
 * Hands every frame to ML Kit and reports a barcode that passes the check-digit test.
 * Frames with a misread digit are dropped silently and the next frame is tried. The
 * same code is reported at most once every [REPEAT_MS].
 */
private class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private var lastCode: String? = null
    private var lastAt = 0L

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        try {
            val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(input)
                .addOnSuccessListener { barcodes ->
                    val reads = barcodes.mapNotNull { barcode ->
                        barcode.rawValue?.takeIf(String::isNotBlank)
                            ?.let { ScannedRead(it, isUpcE = barcode.format == Barcode.FORMAT_UPC_E) }
                    }
                    val code = Barcodes.firstValid(reads) ?: return@addOnSuccessListener
                    val now = System.currentTimeMillis()
                    if (code == lastCode && now - lastAt < REPEAT_MS) return@addOnSuccessListener
                    lastCode = code
                    lastAt = now
                    onBarcode(code)
                }
                .addOnFailureListener { Log.w(TAG, "Barcode scan failed", it) }
                // The frame must always be released, or CameraX stops delivering new ones.
                .addOnCompleteListener { imageProxy.close() }
        } catch (e: Exception) {
            Log.w(TAG, "Barcode analysis failed", e)
            imageProxy.close()
        }
    }

    private companion object { const val REPEAT_MS = 2_000L }
}

@Composable
private fun FallbackMessage(
    title: String,
    body: String,
    onEnterManually: () -> Unit,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // The card is short on a small phone; the message scrolls inside it instead of spilling out.
            .verticalScroll(rememberScrollState())
            .padding(start = 64.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (primaryLabel != null && onPrimary != null) {
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(primaryLabel) }
        }
        OutlinedButton(
            onClick = onEnterManually,
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
        ) { Text("Add a product by hand") }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
