package com.zeroindex.intake.ui.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

private const val TAG = "CameraOwner"

/**
 * The one place the app binds the camera, so two screens can never hold it at once (device feedback B3: the
 * barcode scanner said "camera could not start" after the photo estimator had been open).
 *
 * Why it happened: both screens bound their CameraX use cases to the *screen's* lifecycle, not to their own
 * composable, and the photo screen never unbound on the way out. Its preview and capture stayed bound behind
 * the next screen, so the scanner's preview and analysis made four use cases on one camera, which CameraX
 * refuses. Every camera user now goes through [bind], which
 *
 *  1. unbinds whatever the previous owner still holds (and everything else on the provider) before binding,
 *  2. hands back a [Binding] whose [Binding.release] unbinds exactly what it bound, on dispose or leave,
 *  3. retries a failed bind a couple of times, because the camera takes a moment to free up after a release.
 *
 * Main-thread only, like CameraX itself.
 */
object CameraOwner {

    private const val RETRIES = 2
    private const val RETRY_DELAY_MS = 350L

    private val main = Handler(Looper.getMainLooper())
    private var current: Binding? = null

    class Binding internal constructor() {
        internal var provider: ProcessCameraProvider? = null
        internal var useCases: List<UseCase> = emptyList()
        internal var retry: Runnable? = null
        internal var released = false

        /** Unbinds this owner's use cases and cancels a pending retry. Safe to call twice. */
        fun release() {
            if (released) return
            released = true
            retry?.let(main::removeCallbacks)
            val p = provider
            if (p != null && useCases.isNotEmpty()) runCatching { p.unbind(*useCases.toTypedArray()) }
            useCases = emptyList()
            if (current === this) current = null
        }
    }

    /**
     * Binds [useCases] to [lifecycleOwner], first releasing any other owner. [onBound] gets the camera;
     * [onError] gets a message fit to show once the retries are spent. Call [Binding.release] from `onDispose`.
     */
    fun bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        selector: CameraSelector,
        useCases: List<UseCase>,
        onBound: (Camera) -> Unit,
        onError: (String) -> Unit,
    ): Binding {
        val binding = Binding()
        current?.release()
        current = binding
        val future = ProcessCameraProvider.getInstance(context)

        fun attempt(left: Int) {
            if (binding.released) return
            try {
                val provider = future.get()
                binding.provider = provider
                // Nothing else may hold the camera while this owner has it.
                provider.unbindAll()
                val camera = provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
                binding.useCases = useCases
                onBound(camera)
            } catch (e: Exception) {
                Log.w(TAG, "Camera bind failed, $left retries left", e)
                if (left > 0 && !binding.released) {
                    val again = Runnable { attempt(left - 1) }
                    binding.retry = again
                    main.postDelayed(again, RETRY_DELAY_MS)
                } else if (!binding.released) {
                    onError(BIND_FAILED)
                }
            }
        }

        future.addListener({ attempt(RETRIES) }, ContextCompat.getMainExecutor(context))
        return binding
    }

    /** The message for a camera that would not open, with something the person can do about it. */
    const val BIND_FAILED =
        "The camera is busy or could not be opened. Close other camera apps, then tap Try again."
}
