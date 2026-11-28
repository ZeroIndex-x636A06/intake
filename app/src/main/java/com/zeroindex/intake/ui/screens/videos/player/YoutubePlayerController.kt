package com.zeroindex.intake.ui.screens.videos.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zeroindex.intake.domain.videos.YoutubePlayerErrors
import com.zeroindex.intake.ui.screens.videos.YoutubeEmbed

/**
 * YouTube's own player, driven through the IFrame Player API in a WebView. The frame is YouTube's nocookie player with its
 * controls off; Intake draws the controls. Nothing about the video is changed or blocked and YouTube's mark stays visible.
 * If the API does not report ready in [READY_TIMEOUT_MS], the controller falls back to the plain embed with YouTube's own
 * controls ([fallbackEmbed]) and Intake's controls step aside.
 *
 * The page talks back through `IntakeBridge` with numbers only; each call is moved to the main thread before it touches state.
 */
@SuppressLint("SetJavaScriptEnabled")
class YoutubePlayerController(
    context: Context,
    private val videoId: String,
    startMs: Long,
    private val autoplay: Boolean,
    private val loopWhole: Boolean,
) : PlayerController() {
    override val backend = PlayerBackend.YOUTUBE

    /** True once the IFrame API could not start and the page shows YouTube's own controls instead of Intake's. */
    var fallbackEmbed by mutableStateOf(false)
        private set

    /** In the fallback: no sign that YouTube's frame drew anything yet. Shown as a note, cleared when the frame reports in. */
    var fallbackStalled by mutableStateOf(false)
        private set

    val webView: WebView = WebView(context)
    private var ready = false
    private var released = false
    private var pendingSeekMs: Long? = null
    private var pendingPlay: Boolean? = null
    private var pendingRate: Float? = null
    private var lastKnownMs = startMs

    private val readyTimeout = Runnable {
        if (!ready && !released && error == null) switchToEmbed()
    }

    private val stallTimeout = Runnable {
        if (fallbackEmbed && !embedAlive && !released && error == null) fallbackStalled = true
    }
    private var embedAlive = false

    private inner class Bridge {
        /** The plain embed's frame sent a message: it is running. */
        @JavascriptInterface
        fun onEmbedAlive() = onMain {
            embedAlive = true
            fallbackStalled = false
            webView.removeCallbacks(stallTimeout)
        }

        /** The plain embed's frame reported a player error code. */
        @JavascriptInterface
        fun onEmbedError(code: Int) = onMain { if (fallbackEmbed) fail(PlayerError(YoutubePlayerErrors.message(code), YoutubePlayerErrors.canRetry(code))) }

        @JavascriptInterface
        fun onReady(durationSec: Double) = onMain {
            ready = true
            webView.removeCallbacks(readyTimeout)
            durationMs = secToMs(durationSec)
            buffering = autoplay
            pendingRate?.let { changeRate(it) }
            pendingSeekMs?.let { seekTo(it) }
            pendingPlay?.let { if (it) play() else pause() }
            pendingRate = null; pendingSeekMs = null; pendingPlay = null
        }

        @JavascriptInterface
        fun onState(state: Int) = onMain {
            when (state) {
                1 -> { isPlaying = true; buffering = false; ended = false }
                2 -> { isPlaying = false; buffering = false }
                3 -> buffering = true
                0 -> {
                    buffering = false
                    if (loopWhole) { seekTo(0L); play() } else { isPlaying = false; ended = true }
                }
                else -> buffering = false
            }
        }

        @JavascriptInterface
        fun onTime(currentSec: Double, durationSec: Double) = onMain {
            val d = secToMs(durationSec)
            if (d > 0 && d != durationMs) durationMs = d
            lastKnownMs = secToMs(currentSec)
            publishPosition(lastKnownMs)
        }

        @JavascriptInterface
        fun onRate(r: Double) = onMain { if (r in 0.1..4.0) rate = r.toFloat() }

        @JavascriptInterface
        fun onError(code: Int) = onMain { fail(PlayerError(YoutubePlayerErrors.message(code), YoutubePlayerErrors.canRetry(code))) }
    }

    private fun fail(e: PlayerError) {
        webView.removeCallbacks(readyTimeout)
        webView.removeCallbacks(stallTimeout)
        fallbackStalled = false
        error = e
        isPlaying = false
        buffering = false
    }

    private fun secToMs(sec: Double): Long = if (sec.isNaN() || sec < 0) 0L else (sec * 1000.0).toLong().coerceAtMost(MAX_MS)

    private fun onMain(block: () -> Unit) {
        webView.post { if (!released) block() }
    }

    init {
        webView.apply {
            setBackgroundColor(AndroidColor.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    if (request.isForMainFrame) !YoutubeEmbed.allowsHost(request.url.host) else !YoutubeEmbed.allowsFrameHost(request.url.host)

                // A page or frame that cannot load at all (no connection, blocked, a server error): say so in words.
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (isPlayerDocument(request) && error.errorCode in CONNECTION_ERRORS) onMain { fail(PlayerError(YoutubePlayerErrors.message(YoutubePlayerErrors.SCRIPT_FAILED))) }
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    if (isPlayerDocument(request) && errorResponse.statusCode >= 400) {
                        onMain { fail(PlayerError("YouTube's player could not load this video (error ${errorResponse.statusCode}).")) }
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (!released) {
                        error = PlayerError("The video player stopped. Try again.")
                        isPlaying = false
                        buffering = false
                    }
                    return true
                }
            }
            // The default chrome client: without one, some phones never draw the video surface.
            webChromeClient = WebChromeClient()
            addJavascriptInterface(Bridge(), "IntakeBridge")
        }
        load(startMs)
    }

    /** The page itself, or the embed's own document inside it: not the many pictures, scripts and trackers around a player. */
    private fun isPlayerDocument(request: WebResourceRequest): Boolean =
        request.isForMainFrame || (YoutubeEmbed.allowsHost(request.url.host) && request.url.path.orEmpty().startsWith("/embed/"))

    private fun load(fromMs: Long) {
        if (!YoutubeEmbed.isValidId(videoId)) {
            error = PlayerError("That link does not have a video id Intake can play.", canRetry = false)
            return
        }
        ready = false
        webView.removeCallbacks(readyTimeout)
        webView.postDelayed(readyTimeout, READY_TIMEOUT_MS)
        webView.loadDataWithBaseURL(
            YoutubeEmbed.BASE_URL,
            YoutubeEmbed.apiHtml(videoId, (fromMs / 1000L).toInt(), autoplay),
            "text/html",
            "utf-8",
            null,
        )
    }

    private fun switchToEmbed() {
        fallbackEmbed = true
        embedAlive = false
        fallbackStalled = false
        buffering = false
        webView.removeCallbacks(stallTimeout)
        webView.postDelayed(stallTimeout, STALL_TIMEOUT_MS)
        webView.loadDataWithBaseURL(
            YoutubeEmbed.BASE_URL,
            YoutubeEmbed.html(videoId, (lastKnownMs / 1000L).toInt(), autoplay = true),
            "text/html",
            "utf-8",
            null,
        )
    }

    private fun js(code: String) = webView.evaluateJavascript(code, null)

    override fun play() {
        if (!ready) { pendingPlay = true; return }
        ended = false
        js("window.ip&&ip.play()")
    }

    override fun pause() {
        if (!ready) { pendingPlay = false; return }
        js("window.ip&&ip.pause()")
    }

    override fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        lastKnownMs = target
        if (!ready) { pendingSeekMs = target; return }
        ended = false
        js("window.ip&&ip.seek(${target / 1000.0})")
        publishPosition(target)
    }

    override fun changeRate(rate: Float) {
        if (!ready) { pendingRate = rate; return }
        js("window.ip&&ip.rate($rate)")
        this.rate = rate
    }

    override fun retry() {
        error = null
        buffering = true
        fallbackEmbed = false
        fallbackStalled = false
        webView.removeCallbacks(stallTimeout)
        load(lastKnownMs)
    }

    override fun onHostPause() = webView.onPause()
    override fun onHostResume() = webView.onResume()

    override fun release() {
        released = true
        webView.removeCallbacks(readyTimeout)
        webView.removeCallbacks(stallTimeout)
        webView.removeJavascriptInterface("IntakeBridge")
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private companion object {
        const val READY_TIMEOUT_MS = 12_000L
        const val STALL_TIMEOUT_MS = 15_000L

        /** WebViewClient error codes for a connection that did not work (not a load that was cancelled by a newer one). */
        val CONNECTION_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT, WebViewClient.ERROR_IO,
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
        )
        const val MAX_MS = 24L * 3600 * 1000
    }
}
