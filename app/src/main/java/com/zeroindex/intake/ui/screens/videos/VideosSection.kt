package com.zeroindex.intake.ui.screens.videos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.videos.ScoredVideo
import com.zeroindex.intake.domain.videos.VideoBrowse
import com.zeroindex.intake.domain.videos.VideoLabel
import com.zeroindex.intake.domain.videos.VideoLength
import com.zeroindex.intake.domain.videos.VideoMeta
import com.zeroindex.intake.domain.videos.VideoStyle
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LoadingState
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * Train > Videos (the `a-videos` frame). Search with your own YouTube key, filter to workouts, browse Recommended, keep
 * Saved. With no key: what the key is for, a way to add it, and "Paste a YouTube link", which needs none.
 */
@Composable
fun VideosSection(
    contentPadding: PaddingValues,
    onOpenVideo: (videoId: String) -> Unit,
    onMakeWorkout: (videoId: String) -> Unit,
    onOpenKeySettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var cleared by remember { mutableStateOf(false) }

    // On first show and every time the page comes back to the front (a key may have been added in Settings).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.onShown() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.hasKey) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search workout videos") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty() || state.searchedFor != null) {
                            IconButton(onClick = { viewModel.clearSearch() }) { Icon(Icons.Filled.Close, contentDescription = "Clear the search") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); viewModel.search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "chips") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkoutsOnlyChip(state.onlyWorkouts, viewModel::toggleOnlyWorkouts)
                        VideoStyle.entries.forEach { s -> ChoiceChip(s.label, selected = state.style == s, onClick = { viewModel.toggleStyle(s) }) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoLength.entries.forEach { l -> ChoiceChip(l.label, selected = state.length == l, onClick = { viewModel.setLength(l) }) }
                    }
                }
            }
        }

        when {
            !state.hasKey -> item(key = "nokey") {
                NoKeyCard(
                    link = state.link,
                    linkBusy = state.linkBusy,
                    linkError = state.linkError,
                    onLink = viewModel::setLink,
                    onOpenLink = { viewModel.openLink(onOpenVideo) },
                    onOpenKeySettings = onOpenKeySettings,
                )
            }

            state.searching -> item(key = "searching") { LoadingState(listOf("Searching YouTube")) }

            state.searchError != null -> item(key = "search-error") {
                ErrorState(
                    title = "Could not search",
                    body = state.searchError.orEmpty() + " Your saved videos below still work.",
                    action = { TextButton(onClick = viewModel::search) { Text("Try again") } },
                )
            }

            state.results != null -> {
                val results = state.results!!
                item(key = "results-head") {
                    Column {
                        Text("Results for \"${state.searchedFor.orEmpty()}\"", style = MaterialTheme.typography.titleLarge)
                        if (state.resultsFromCache) {
                            Text(
                                "Shown from this phone, so no search was used.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                results.hiddenLine()?.let { line ->
                    item(key = "hidden") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::showEverything, modifier = Modifier.heightIn(min = 48.dp)) { Text("Show everything") }
                        }
                    }
                }
                if (results.shown.isEmpty()) {
                    item(key = "none") {
                        EmptyState(
                            title = "No videos to show",
                            body = if (results.hiddenAsNotWorkouts > 0) "Everything found was filtered out as not a workout." else "Nothing matched. Try other words, or a different length.",
                        )
                    }
                } else {
                    items(results.shown, key = { "r-${it.video.id}" }) { scored ->
                        VideoRow(scored, onClick = { onOpenVideo(scored.video.id) })
                    }
                }
            }

            else -> recommendedItems(state, viewModel, onOpenVideo)
        }

        if (state.saved.isNotEmpty()) {
            item(key = "saved-head") { Text("Saved", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            item(key = "saved-list") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = IntakeShape.Card,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        state.saved.forEachIndexed { i, v ->
                            SavedRow(v, onOpen = { onOpenVideo(v.id) }, onMake = { onMakeWorkout(v.id) }, onRemove = { viewModel.unsave(v.id) })
                            if (i != state.saved.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        } else if (state.hasKey && state.results == null) {
            item(key = "saved-empty") {
                Text(
                    "Videos you save to watch later appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "footer") {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Videos need internet and your own key. Intake never downloads a video. Only the words you search for are sent to YouTube, and only when you search. Video information and playback come from YouTube.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.hasKey) {
                    Text(state.quotaHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { viewModel.clearCache { cleared = true } }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (cleared) "Cached video information cleared" else "Clear cached video information")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.recommendedItems(
    state: VideosUiState,
    viewModel: VideosViewModel,
    onOpenVideo: (String) -> Unit,
) {
    when (val rec = state.recommended) {
        RecommendedUi.Idle, RecommendedUi.Loading -> item(key = "rec-loading") { LoadingState(listOf("Finding videos for you")) }
        is RecommendedUi.Failed -> item(key = "rec-failed") {
            ErrorState(
                title = "Could not load recommendations",
                body = rec.message,
                action = { TextButton(onClick = { viewModel.loadRecommended(force = true) }) { Text("Try again") } },
            )
        }
        is RecommendedUi.Ready -> {
            item(key = "rec-head") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("Recommended for you", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(
                        if (rec.personalised) "from your muscle volume" else "popular picks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rec.rows.isEmpty()) {
                item(key = "rec-empty") {
                    EmptyState(body = "Nothing recommended right now. Search above, or turn off \"Workouts only\" for a wider net.")
                }
            }
            rec.rows.forEachIndexed { index, row ->
                item(key = "rec-$index") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.reason, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(row.videos, key = { it.video.id }) { v -> VideoCard(v, onClick = { onOpenVideo(v.video.id) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutsOnlyChip(selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.Chip,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Switch, onClickLabel = "Workouts only", onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text("Workouts only", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The badge a card wears: Workout, Maybe, or how many chapters it has. */
@Composable
private fun Badges(v: ScoredVideo) {
    val chapters = VideoBrowse.chapterCount(v.video)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (v.verdict.label) {
            VideoLabel.WORKOUT -> Tag("Workout")
            VideoLabel.MAYBE -> Tag("Maybe")
            VideoLabel.NOT_WORKOUT -> Tag("Not a workout")
        }
        if (chapters >= 2) Tag("$chapters chapters")
    }
}

@Composable
private fun VideoRow(v: ScoredVideo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open ${v.video.title}", onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoThumbnail(v.video.thumbnailUrl, v.video.durationSec, modifier = Modifier.width(156.dp), corner = 14.dp, showPlay = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(v.video.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, color = MaterialTheme.colorScheme.onSurface)
            Text(v.video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Badges(v)
        }
    }
}

@Composable
private fun VideoCard(v: ScoredVideo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(264.dp)
            .clickable(role = Role.Button, onClickLabel = "Open ${v.video.title}", onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VideoThumbnail(v.video.thumbnailUrl, v.video.durationSec, modifier = Modifier.fillMaxWidth())
        Text(v.video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
        Text(v.video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Badges(v)
    }
}

@Composable
private fun SavedRow(v: VideoMeta, onOpen: () -> Unit, onMake: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(112.dp).clickable(role = Role.Button, onClickLabel = "Open ${v.title}", onClick = onOpen)) {
            VideoThumbnail(v.thumbnailUrl, v.durationSec, corner = 12.dp, showPlay = false)
        }
        Column(Modifier.weight(1f)) {
            Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onMake, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Make this a workout", style = MaterialTheme.typography.titleSmall)
            }
        }
        Column {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${v.title}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Remove from saved") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

/** No key: what it is for, how to add it, and the pasted-link way in. */
@Composable
private fun NoKeyCard(
    link: String,
    linkBusy: Boolean,
    linkError: String?,
    onLink: (String) -> Unit,
    onOpenLink: () -> Unit,
    onOpenKeySettings: () -> Unit,
) {
    var howTo by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IntakeCard {
            Text("Videos use YouTube and your own free key", style = MaterialTheme.typography.titleLarge)
            Text(
                "The key lets Intake search YouTube for you. It is stored encrypted on this phone and sent only to YouTube, and a free one allows about 100 searches a day. Intake never downloads a video; it plays through YouTube's own player.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            Button(onClick = onOpenKeySettings, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Add a key in Settings > API keys")
            }
            TextButton(onClick = { howTo = !howTo }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (howTo) "Hide how to get a key" else "How do I get a key?")
            }
            if (howTo) {
                SandNote(
                    "1. Open the Google Cloud console and create a project.\n" +
                        "2. Turn on \"YouTube Data API v3\" for it.\n" +
                        "3. Create an API key under Credentials.\n" +
                        "4. Paste it in Settings > API keys > YouTube, then tap Test.\n" +
                        "It costs nothing at this size of use.",
                )
            }
        }
        IntakeCard {
            Text("Paste a YouTube link", style = MaterialTheme.typography.titleMedium)
            Text(
                "No key needed. Intake asks YouTube for the title only, then plays the video in YouTube's player.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            OutlinedTextField(
                value = link,
                onValueChange = onLink,
                label = { Text("youtube.com or youtu.be link") },
                singleLine = true,
                isError = linkError != null,
                supportingText = linkError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onOpenLink() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenLink,
                enabled = link.isNotBlank() && !linkBusy,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (linkBusy) "Looking it up" else "Open this video") }
        }
    }
}
