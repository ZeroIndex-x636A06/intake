package com.zeroindex.intake.ui.screens.videos

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Pure helpers for the embedded player, kept apart from the WebView so they can be tested. */
object YoutubeEmbed {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun isValidId(id: String): Boolean = ID.matches(id)

    /** The page the WebView loads: YouTube's own embed in an iframe, nothing else. [startSec] jumps to a chapter. */
    fun html(videoId: String, startSec: Int = 0): String {
        require(isValidId(videoId)) { "not a YouTube video id" }
        val autoplay = if (startSec > 0) 1 else 0
        val src = "https://www.youtube-nocookie.com/embed/$videoId" +
            "?playsinline=1&rel=0&modestbranding=1&fs=0&start=${startSec.coerceAtLeast(0)}&autoplay=$autoplay"
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:#000}iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>" +
            "</head><body><iframe src=\"$src\" title=\"YouTube video player\" " +
            "allow=\"accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture\" " +
            "referrerpolicy=\"strict-origin-when-cross-origin\"></iframe></body></html>"
    }

    /** The page's own address for the player's referrer check: this app, as YouTube's guidance for apps asks. */
    const val BASE_URL = "https://com.zeroindex.intake/"

    /** Only YouTube's embed host may load inside the player view; every other navigation is refused. */
    fun allowsHost(host: String?): Boolean = host != null && (host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com"))
}

/**
 * The official YouTube embed in a WebView (IA v5 7b). JavaScript is switched on for this one view only, the view cannot open
 * files or other sites, and nothing is downloaded: the video streams from YouTube. A new [startSec] reloads at that point.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubePlayer(videoId: String, startSec: Int, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    val holder = remember { arrayOfNulls<WebView>(1) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        factory = { context ->
            WebView(context).apply {
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
                        !YoutubeEmbed.allowsHost(request.url.host)
                }
                holder[0] = this
            }
        },
        update = { view ->
            val key = "$videoId@$startSec"
            if (view.tag != key && YoutubeEmbed.isValidId(videoId)) {
                view.tag = key
                view.loadDataWithBaseURL(YoutubeEmbed.BASE_URL, YoutubeEmbed.html(videoId, startSec), "text/html", "utf-8", null)
            }
        },
        onRelease = { view ->
            holder[0] = null
            view.stopLoading()
            view.destroy()
        },
    )
}
