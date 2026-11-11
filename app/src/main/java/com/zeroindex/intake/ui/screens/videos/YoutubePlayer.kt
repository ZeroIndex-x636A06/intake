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
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Pure helpers for the embedded player, kept apart from the WebView so they can be tested. */
object YoutubeEmbed {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun isValidId(id: String): Boolean = ID.matches(id)

    /** The page the WebView loads: YouTube's own embed in an iframe, nothing else. [startSec] jumps to a chapter. */
    fun html(videoId: String, startSec: Int = 0, autoplay: Boolean = startSec > 0): String {
        require(isValidId(videoId)) { "not a YouTube video id" }
        val autoplay = if (autoplay) 1 else 0
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

    /** The embed host itself: the only place the page as a whole may go. */
    fun allowsHost(host: String?): Boolean = host != null && (host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com"))

    /**
     * Where the player frame may go. The embed moves between YouTube's and Google's own hosts while it starts (the
     * player, a consent step); refusing those left a black box on a real phone. Everything else is still refused.
     */
    fun allowsFrameHost(host: String?): Boolean {
        if (host == null) return false
        return FRAME_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private val FRAME_HOSTS = listOf("youtube-nocookie.com", "youtube.com", "google.com", "googlevideo.com", "ytimg.com", "gstatic.com")
}

/**
 * The official YouTube embed in a WebView (IA v5 7b). JavaScript is switched on for this one view only, the view cannot open
 * files or other sites, and nothing is downloaded: the video streams from YouTube. A new [startSec] reloads at that point.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubePlayer(videoId: String, startSec: Int, modifier: Modifier = Modifier, thumbnailUrl: String? = null, durationSec: Int = 0) {
    // The video's picture comes first, with a play button; YouTube's player loads when it is tapped (or a chapter is).
    // So the page always shows what the video is, costs nothing until it is played, and starts playing on that tap.
    var started by androidx.compose.runtime.saveable.rememberSaveable(videoId) { androidx.compose.runtime.mutableStateOf(false) }
    if (!started && startSec == 0) {
        VideoThumbnail(
            url = thumbnailUrl ?: com.zeroindex.intake.domain.videos.YoutubeIds.thumbnailUrl(videoId),
            durationSec = durationSec,
            modifier = modifier
                .fillMaxWidth()
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Play the video") { started = true },
        )
        return
    }
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
                        if (request.isForMainFrame) !YoutubeEmbed.allowsHost(request.url.host) else !YoutubeEmbed.allowsFrameHost(request.url.host)
                }
                // The default chrome client: without one, some phones never draw the video surface.
                webChromeClient = android.webkit.WebChromeClient()
                holder[0] = this
            }
        },
        update = { view ->
            val key = "$videoId@$startSec"
            if (view.tag != key && YoutubeEmbed.isValidId(videoId)) {
                view.tag = key
                view.loadDataWithBaseURL(YoutubeEmbed.BASE_URL, YoutubeEmbed.html(videoId, startSec, autoplay = true), "text/html", "utf-8", null)
            }
        },
        onRelease = { view ->
            holder[0] = null
            view.stopLoading()
            view.destroy()
        },
    )
}
