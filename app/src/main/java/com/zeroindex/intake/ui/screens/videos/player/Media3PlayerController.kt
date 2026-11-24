package com.zeroindex.intake.ui.screens.videos.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.zeroindex.intake.domain.videos.PlayerKind

/**
 * Plays a downloaded file or a yt-dlp stream with Media3 ExoPlayer. The video surface is a PlayerView with its own controller
 * switched off; the controls are Intake's. [location] is never logged.
 */
class Media3PlayerController(
    context: Context,
    kind: PlayerKind,
    location: String,
    loopWhole: Boolean,
    startMs: Long,
    playWhenReady: Boolean,
) : PlayerController() {
    override val backend = PlayerBackend.MEDIA3

    val exo: ExoPlayer = ExoPlayer.Builder(context).build()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@Media3PlayerController.isPlaying = isPlaying
            if (isPlaying) ended = false
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            buffering = playbackState == Player.STATE_BUFFERING
            ended = playbackState == Player.STATE_ENDED
            if (playbackState != Player.STATE_IDLE) syncDuration()
            if (playbackState == Player.STATE_ENDED) publishPosition(exo.duration.takeIf { it != C.TIME_UNSET } ?: exo.currentPosition)
        }

        override fun onPlayerError(error: PlaybackException) {
            // No stream address or path in the message: those are never shown or logged.
            this@Media3PlayerController.error = PlayerError("This video could not be played. Check your connection and try again.")
            buffering = false
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            rate = playbackParameters.speed
        }
    }

    init {
        val uri = if (kind == PlayerKind.OFFLINE_FILE) Uri.fromFile(java.io.File(location)) else Uri.parse(location)
        exo.addListener(listener)
        exo.repeatMode = if (loopWhole) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exo.setMediaItem(MediaItem.fromUri(uri), startMs.coerceAtLeast(0L))
        exo.prepare()
        exo.playWhenReady = playWhenReady
        publishPosition(startMs)
    }

    private fun syncDuration() {
        val d = exo.duration
        durationMs = if (d == C.TIME_UNSET || d < 0) 0L else d
    }

    override fun play() {
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0L)
        exo.play()
    }

    override fun pause() = exo.pause()

    override fun seekTo(ms: Long) {
        exo.seekTo(ms.coerceAtLeast(0L))
        publishPosition(ms)
        if (ms < durationMs || durationMs == 0L) ended = false
    }

    override fun changeRate(rate: Float) {
        exo.setPlaybackSpeed(rate)
    }

    override fun retry() {
        error = null
        buffering = true
        exo.prepare()
        exo.play()
    }

    override fun poll() {
        if (durationMs == 0L) syncDuration()
        publishPosition(exo.currentPosition)
    }

    override fun release() {
        exo.removeListener(listener)
        exo.release()
    }
}
