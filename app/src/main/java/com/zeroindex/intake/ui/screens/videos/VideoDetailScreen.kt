package com.zeroindex.intake.ui.screens.videos

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.zeroindex.intake.data.video.direct.DownloadState
import com.zeroindex.intake.domain.videos.DirectPlayback
import com.zeroindex.intake.domain.videos.PlayerKind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.videos.VideoLabel
import com.zeroindex.intake.domain.videos.YoutubeIds
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * Video detail (IA v5 7b): the official player, the title and what the workout filter thinks in plain words, Make this a
 * workout, save, open or share, the chapters (a tap jumps the player) and the description.
 */
@Composable
fun VideoDetailScreen(
    contentPadding: PaddingValues,
    onMakeWorkout: (videoId: String) -> Unit,
    onOpenWorkout: (programId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var startSec by remember { mutableIntStateOf(0) }
    var showDescription by remember { mutableStateOf(false) }
    val meta = state.meta

    if (state.loading) {
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // A vertical video (a Short) opens in the full-height Shorts player, which swipes through the Shorts of the list it came from.
    if (meta != null && com.zeroindex.intake.domain.videos.ShortsRule.isShort(meta)) {
        ShortsPlayerScreen(contentPadding = contentPadding, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "player") {
            val source = state.source
            if (source != null && source.kind != PlayerKind.YOUTUBE && source.location != null) {
                // Downloaded file or yt-dlp stream: the in-app player, landscape-capable, no ads.
                DirectVideoPlayer(
                    kind = source.kind,
                    location = source.location,
                    startSec = startSec,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
                    playing = startSec > 0,
                )
            } else if (YoutubeEmbed.isValidId(state.videoId)) {
                YoutubePlayer(videoId = state.videoId, startSec = startSec, thumbnailUrl = meta?.thumbnailUrl, durationSec = meta?.durationSec ?: 0)
            } else {
                ErrorState(title = "Not a YouTube video", body = "That link does not have a video id Intake can play.")
            }
        }
        state.error?.let { message ->
            item(key = "error") {
                ErrorState(
                    title = "Details not loaded",
                    body = "$message The player may still work if you are online.",
                    action = { TextButton(onClick = viewModel::load) { Text("Try again") } },
                )
            }
        }
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(meta?.title ?: "YouTube video", style = MaterialTheme.typography.titleLarge)
                if (meta != null) {
                    Text(
                        buildString {
                            append(meta.channelTitle)
                            if (meta.durationSec > 0) append(if (meta.channelTitle.isEmpty()) "" else " · ").append(YoutubeIds.clock(meta.durationSec))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.verdict?.let { v ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag(v.label.label)
                        Text(v.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (v.label == VideoLabel.WORKOUT && state.chapters.isNotEmpty()) {
                        Text(
                            "${state.chapters.size} chapters found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item(key = "make") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { onMakeWorkout(state.videoId) },
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Make this a workout") }
                Text(
                    if (state.hasDescription) {
                        "Reads the chapters and description on this phone first. No AI unless you ask."
                    } else {
                        "This link has only a title, so there are no chapters to read. You can build the list by hand, or ask AI to suggest one."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.workoutId?.let { id ->
                    TextButton(onClick = { onOpenWorkout(id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("You already made a workout from this. Open it")
                    }
                }
            }
        }
        item(key = "actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::toggleSave,
                    shape = IntakeShape.Button,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(if (state.saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = null)
                    Text(if (state.saved) "  Saved" else "  Save", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, YoutubeIds.watchUrl(state.videoId))
                        context.startActivity(Intent.createChooser(send, "Share link"))
                    },
                    shape = IntakeShape.Button,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("  Share", maxLines = 1)
                }
            }
        }
        state.source?.note?.let { note ->
            item(key = "source-note") { Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (state.directOn || state.downloadedBytes != null) {
            item(key = "download") {
                DownloadControls(state.download, state.downloadedBytes, onDownload = viewModel::download, onCancel = viewModel::cancelDownload, directOn = state.directOn)
            }
        }
        item(key = "open-youtube") {
            // Its own full-width button: as the middle of three it had no room for its words, and it is the way out
            // when a video cannot be played inside another app.
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(YoutubeIds.watchUrl(state.videoId)))) },
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Text("  Open in YouTube", maxLines = 1)
            }
        }
        if (state.chapters.isNotEmpty()) {
            item(key = "chapters-head") { Text("Chapters", style = MaterialTheme.typography.titleLarge) }
            item(key = "chapters") {
                Column {
                    state.chapters.forEachIndexed { i, c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClickLabel = "Play from ${YoutubeIds.clock(c.startSec)}") { startSec = c.startSec }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(YoutubeIds.clock(c.startSec), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(c.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                        if (i != state.chapters.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        if (!meta?.description.isNullOrBlank()) {
            item(key = "description") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showDescription = !showDescription }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (showDescription) "Hide description" else "Show description")
                    }
                    if (showDescription) {
                        Text(meta?.description.orEmpty().take(4_000), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item(key = "credit") {
            Text(
                if (state.directOn) {
                    "Video and information from YouTube. Direct playback and downloads use yt-dlp and are not an official YouTube feature."
                } else {
                    "Video and information from YouTube. Intake streams it through YouTube's own player and never downloads it."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Download, progress with Cancel, or the size of what is on the phone. Shown only with direct playback on (or when a file exists). */
@Composable
private fun DownloadControls(download: DownloadState?, downloadedBytes: Long?, onDownload: () -> Unit, onCancel: () -> Unit, directOn: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            download is DownloadState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(DirectPlayback.progressText(download.percent), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
                }
                if (download.percent < 0f) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                else androidx.compose.material3.LinearProgressIndicator(progress = { download.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            downloadedBytes != null -> Text(
                "Downloaded to this phone, ${DirectPlayback.size(downloadedBytes)}. Plays offline. Delete it from Videos > Downloaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            directOn -> {
                OutlinedButton(onClick = onDownload, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Download to this phone") }
                if (download is DownloadState.Failed) Text(download.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
