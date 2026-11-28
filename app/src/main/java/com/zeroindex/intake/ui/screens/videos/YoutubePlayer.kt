package com.zeroindex.intake.ui.screens.videos

/**
 * Pure helpers for YouTube's player in a WebView, kept apart from the WebView so they can be tested. The player itself is
 * `ui/screens/videos/player/YoutubePlayerController` (YouTube's IFrame Player API, with Intake's own controls on top).
 */
object YoutubeEmbed {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun isValidId(id: String): Boolean = ID.matches(id)

    /**
     * The fallback page: YouTube's own embed in an iframe with YouTube's own controls, nothing else. Used only when the
     * IFrame API cannot start. [startSec] jumps to a chapter.
     */
    fun html(videoId: String, startSec: Int = 0, autoplay: Boolean = startSec > 0): String {
        require(isValidId(videoId)) { "not a YouTube video id" }
        val autoplay = if (autoplay) 1 else 0
        val src = "https://www.youtube-nocookie.com/embed/$videoId" +
            "?playsinline=1&rel=0&modestbranding=1&fs=0&start=${startSec.coerceAtLeast(0)}&autoplay=$autoplay" +
            "&enablejsapi=1&origin=https%3A%2F%2Fcom.zeroindex.intake"
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:#000}iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>" +
            "</head><body><iframe id=\"f\" src=\"$src\" title=\"YouTube video player\" " +
            "allow=\"accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture\" " +
            "referrerpolicy=\"strict-origin-when-cross-origin\"></iframe><script>" +
            // The frame's own messages tell the app that it is alive, or that it hit an error (numbers only go to the app).
            "var f=document.getElementById('f');" +
            "function B(){return window.IntakeBridge}" +
            "window.addEventListener('message',function(e){try{" +
            "if(!/(^|\\.)youtube(-nocookie)?\\.com\$/.test(new URL(e.origin).hostname))return;" +
            "var d=typeof e.data==='string'?JSON.parse(e.data):e.data;B().onEmbedAlive();" +
            "if(d&&d.event==='onError')B().onEmbedError(d.info|0)}catch(x){}});" +
            "f.onload=function(){try{f.contentWindow.postMessage(JSON.stringify({event:'listening',id:1,channel:'widget'}),'*')}catch(x){}};" +
            "</script></body></html>"
    }

    /**
     * The page for YouTube's IFrame Player API with YouTube's own controls switched off (`controls=0`), so Intake draws its own.
     * The frame is still YouTube's nocookie player, `modestbranding` only trims the control bar (YouTube's mark stays), and the
     * video is not touched. The page tells the app about the player through `IntakeBridge` (numbers only) and takes commands
     * through `window.ip`.
     */
    fun apiHtml(videoId: String, startSec: Int = 0, autoplay: Boolean = true): String {
        require(isValidId(videoId)) { "not a YouTube video id" }
        val start = startSec.coerceAtLeast(0)
        val auto = if (autoplay) 1 else 0
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>" +
            "</head><body><div id=\"p\"></div><script>" +
            "var player=null,ready=false,st=-1;" +
            "function B(){return window.IntakeBridge}" +
            "function tick(){try{if(player&&ready)B().onTime(player.getCurrentTime()||0,player.getDuration()||0)}catch(e){}}" +
            "function onYouTubeIframeAPIReady(){" +
            "player=new YT.Player('p',{host:'https://www.youtube-nocookie.com',videoId:'$videoId'," +
            "playerVars:{controls:0,modestbranding:1,playsinline:1,rel:0,fs:0,disablekb:1,start:$start,autoplay:$auto}," +
            "events:{" +
            "onReady:function(){ready=true;B().onReady(player.getDuration()||0)}," +
            "onStateChange:function(e){st=e.data;B().onState(e.data);tick()}," +
            "onPlaybackRateChange:function(e){B().onRate(e.data)}," +
            "onError:function(e){B().onError(e.data)}}});" +
            "setInterval(function(){if(st==1)tick()},250)}" +
            "window.ip={play:function(){if(ready)player.playVideo()},pause:function(){if(ready)player.pauseVideo()}," +
            "seek:function(s){if(ready)player.seekTo(s,true)},rate:function(r){if(ready)player.setPlaybackRate(r)}};" +
            "var t=document.createElement('script');t.src='https://www.youtube.com/iframe_api';" +
            "t.onerror=function(){B().onError(-1)};document.head.appendChild(t);" +
            "</script></body></html>"
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
