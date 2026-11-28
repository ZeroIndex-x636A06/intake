package com.zeroindex.intake.ui.screens.videos

import com.zeroindex.intake.ui.components.scrollIntoViewOnFocus
import androidx.compose.foundation.layout.imePadding
import com.zeroindex.intake.ui.components.rememberSyncedText
import com.zeroindex.intake.domain.videos.YoutubeIds
import androidx.activity.compose.BackHandler
import com.zeroindex.intake.data.video.direct.DownloadState
import com.zeroindex.intake.data.video.direct.DownloadedVideo
import com.zeroindex.intake.domain.videos.DirectPlayback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.style.TextOverflow
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import com.zeroindex.intake.domain.videos.VideoPaging
import kotlinx.coroutines.flow.distinctUntilChanged
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.LineButton
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.screens.train.RowChevronIcon
import com.zeroindex.intake.ui.screens.train.TrainRow
import com.zeroindex.intake.ui.screens.train.searchFieldColors
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing

/**
 * Train > Videos (the `a-videos` frame). Search with your own YouTube key, filter to workouts, browse Recommended, keep
 * Saved. With no key: what the key is for, a way to add it, and "Paste a YouTube link", which needs none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosSection(
    contentPadding: PaddingValues,
    onOpenVideo: (videoId: String) -> Unit,
    onMakeWorkout: (videoId: String) -> Unit,
    onOpenKeySettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideosViewModel = hiltViewModel(),
) {
    val openVideo: (String) -> Unit = { id -> viewModel.opening(); onOpenVideo(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var cleared by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var allSaved by rememberSaveable { mutableStateOf(false) }
    var showDownloads by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // On first show and every time the page comes back to the front (a key may have been added in Settings).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.onShown() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Infinite scroll: the next page of the search loads when the last visible item is within a few of the end. The pair
    // changes when a page arrives, so a page that is mostly filtered out still pulls the next one.
    LaunchedEffect(listState) {
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (last, total) -> if (VideoPaging.nearEnd(last, total)) viewModel.loadMore() }
    }

    BackHandler(enabled = allSaved || showDownloads) { allSaved = false; showDownloads = false }
    val downloads = SavedDownloads(
        directOn = state.directOn,
        done = state.downloaded.map { it.videoId }.toSet(),
        running = state.downloading,
        onDownload = viewModel::download,
        onCancel = viewModel::cancelDownload,
    )

    if (showFilters) {
        FiltersSheet(
            style = state.style,
            muscle = state.muscle,
            length = state.length,
            onApply = { s, m, l -> viewModel.applyFilters(s, m, l); showFilters = false },
            onDismiss = { showFilters = false },
        )
    }
    if (showNote) {
        AlertDialog(
            onDismissRequest = { showNote = false },
            confirmButton = { TextButton(onClick = { showNote = false }) { Text("Done") } },
            title = { Text("How Videos works") },
            text = {
                Text(
                    "Videos need internet and your own key. Intake downloads a video only if you turn on Offline downloads and direct playback (Settings > Videos, off by default). Only the words you search for are sent to YouTube, and only when you search or scroll for more. Video information and playback come from YouTube.",
                )
            },
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().imePadding(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        if (showDownloads) {
            downloadedItems(
                items = state.downloaded,
                usedBytes = state.downloadedBytes,
                onBack = { showDownloads = false },
                onOpen = openVideo,
                onDelete = viewModel::deleteDownload,
            )
            return@LazyColumn
        }
        if (allSaved) {
            savedAllItems(
                downloads = downloads,
                saved = state.saved,
                onBack = { allSaved = false },
                onOpen = openVideo,
                onMake = onMakeWorkout,
                onMoreLikeThis = { v -> allSaved = false; viewModel.moreLikeThis(v) },
                onRemove = viewModel::unsave,
            )
            return@LazyColumn
        }

        // Saved is always first, with an empty state. Downloads is reachable whenever the setting is on or something is
        // downloaded, with or without a key.
        item(key = "saved-strip") {
            SavedStrip(
                downloads = downloads,
                saved = state.saved,
                onSeeAll = { allSaved = true },
                onOpen = openVideo,
                onMake = onMakeWorkout,
                onMoreLikeThis = viewModel::moreLikeThis,
                onRemove = viewModel::unsave,
            )
        }
        if (state.directOn || state.downloaded.isNotEmpty()) {
            item(key = "downloads-entry") { DownloadsEntry(count = state.downloaded.size, onClick = { showDownloads = true }) }
        }

        if (state.hasKey) {
            item(key = "search") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val search = rememberSyncedText(state.query, viewModel::setQuery)
                    OutlinedTextField(
                        state = search.box,
                        placeholder = { Text("Search workout videos") },
                        leadingIcon = { Icon(IntakeIcons.Search, contentDescription = null, modifier = Modifier.size(IconSize.Default)) },
                        trailingIcon = {
                            if (state.query.isNotEmpty() || state.searchedFor != null) {
                                IconButton(onClick = { viewModel.clearSearch() }) { Icon(IntakeIcons.Close, contentDescription = "Clear the search", modifier = Modifier.size(IconSize.Default)) }
                            }
                        },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        shape = IntakeShape.Pill,
                        colors = searchFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = KeyboardActionHandler { focus.clearFocus(); viewModel.search() },
                        modifier = Modifier.weight(1f),
                    )
                    VideosOverflow(
                        searchesLeft = state.searchesLeft,
                        cleared = cleared,
                        onHow = { showNote = true },
                        onClear = { viewModel.clearCache { cleared = true } },
                    )
                }
            }
            item(key = "filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip("Workouts only", state.onlyWorkouts, viewModel::toggleOnlyWorkouts)
                    ToggleChip("Hide Shorts", state.hideShorts, viewModel::toggleHideShorts)
                    FiltersChip(state.filterCount) { showFilters = true }
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
                    onOpenLink = { viewModel.openLink(openVideo) },
                    onOpenKeySettings = onOpenKeySettings,
                )
            }

            state.searching -> item(key = "searching") { LoadingState(listOf("Searching YouTube")) }

            state.searchError != null -> item(key = "search-error") {
                ErrorState(
                    title = "Could not search",
                    body = state.searchError.orEmpty() + " Your saved videos above still work.",
                    action = { TextButton(onClick = viewModel::search) { Text("Try again") } },
                )
            }

            state.results != null -> {
                val results = state.results!!
                item(key = "results-head") {
                    SectionLine(
                        "Results for \"${state.searchedFor.orEmpty()}\"",
                        note = if (state.resultsFromCache) "Shown from this phone, so no search was used." else null,
                    )
                }
                results.shortsLine()?.let { line ->
                    item(key = "hidden-shorts") {
                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                if (results.shown.isEmpty() && !state.canLoadMore) {
                    item(key = "none") {
                        EmptyState(
                            title = "No videos to show",
                            body = if (results.hiddenAsNotWorkouts > 0) "Everything found was filtered out as not a workout." else "Nothing matched. Try other words, or a different length.",
                        )
                    }
                } else {
                    items(results.shown, key = { "r-${it.video.id}" }) { scored ->
                        VideoRow(scored, onClick = { openVideo(scored.video.id) }, onMake = { onMakeWorkout(scored.video.id) })
                    }
                }
                item(key = "results-foot") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            state.loadingMore -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            state.moreError != null -> {
                                Text(state.moreError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = viewModel::loadMore, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                            }
                            state.pagingStoppedForQuota -> Text(
                                "No more pages: today's free searches are nearly used up.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            !state.canLoadMore -> Text(
                                "That is all the results.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            else -> recommendedItems(state, viewModel, openVideo, onMakeWorkout)
        }
    }
}

/** The overflow menu next to the search box: the quota estimate, how Videos works, and clearing the cache. */
@Composable
private fun VideosOverflow(searchesLeft: Int, cleared: Boolean, onHow: () -> Unit, onClear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(IntakeIcons.More, contentDescription = "More for Videos", modifier = Modifier.size(IconSize.Default)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("About $searchesLeft searches left today") }, onClick = {}, enabled = false)
            DropdownMenuItem(text = { Text("How Videos works") }, onClick = { open = false; onHow() })
            DropdownMenuItem(
                text = { Text(if (cleared) "Cached video information cleared" else "Clear cached video information") },
                onClick = { open = false; onClear() },
            )
        }
    }
}

/** The "Filters" chip: how many of muscle group, style and duration are set. */
@Composable
private fun FiltersChip(count: Int, onClick: () -> Unit) {
    ToggleChip(if (count > 0) "Filters · $count" else "Filters", selected = count > 0, onClick = onClick, showCheck = false)
}

/** Muscle group, style and duration in one sheet. Apply runs one search when the muscle group or style changed. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(
    style: VideoStyle?,
    muscle: BodyRegion?,
    length: VideoLength?,
    onApply: (VideoStyle?, BodyRegion?, VideoLength?) -> Unit,
    onDismiss: () -> Unit,
) {
    var s by remember { mutableStateOf(style) }
    var m by remember { mutableStateOf(muscle) }
    var l by remember { mutableStateOf(length) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = IntakeSpacing.ScreenGutter).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            Text("Muscle group", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BodyRegion.entries.forEach { r -> ChoiceChip(r.label, selected = m == r, onClick = { m = if (m == r) null else r }) }
            }
            Text("Style", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoStyle.entries.forEach { v -> ChoiceChip(v.label, selected = s == v, onClick = { s = if (s == v) null else v }) }
            }
            Text("Duration", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoLength.entries.forEach { d -> ChoiceChip(d.label, selected = l == d, onClick = { l = if (l == d) null else d }) }
            }
            Text(
                "Muscle group and style add words to the search, so applying them uses one search. Duration only hides videos on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { s = null; m = null; l = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear all") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(s, m, l) }, shape = IntakeShape.Button, modifier = Modifier.heightIn(min = 48.dp)) { Text("Apply") }
            }
        }
    }
}

/** "Saved · N" with "See all", then a compact horizontal row of saved videos. */
@Composable
private fun SavedStrip(
    downloads: SavedDownloads,
    saved: List<VideoMeta>,
    onSeeAll: () -> Unit,
    onOpen: (String) -> Unit,
    onMake: (String) -> Unit,
    onMoreLikeThis: (VideoMeta) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLine("Saved · ${saved.size}", trailing = {
            if (saved.isNotEmpty()) GhostButton(onClick = onSeeAll) { Text("See all") }
        })
        if (saved.isEmpty()) {
            EmptyState(title = "No saved videos yet", body = "Videos you save to watch later appear here.", icon = IntakeIcons.Bookmark)
        }
        if (saved.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(saved.take(12), key = { it.id }) { v ->
                var menu by remember { mutableStateOf(false) }
                Column(Modifier.width(CARD_WIDTH_SAVED)) {
                    VideoThumbnail(v.thumbnailUrl ?: YoutubeIds.thumbnailUrl(v.id), v.durationSec, modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open ${v.title}") { onOpen(v.id) })
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            v.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(top = 6.dp),
                        )
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(IntakeIcons.More, contentDescription = "More for ${v.title}", modifier = Modifier.size(IconSize.Default)) }
                            SavedMenu(menu, { menu = false }, downloads, v, onOpen = { onOpen(v.id) }, onMake = { onMake(v.id) }, onMoreLikeThis = { onMoreLikeThis(v) }, onRemove = { onRemove(v.id) })
                        }
                    }
                }
            }
        }
    }
}

private val CARD_WIDTH_SAVED = 200.dp
private val CARD_WIDTH = 240.dp

@Composable
private fun SavedMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    downloads: SavedDownloads,
    video: VideoMeta,
    onOpen: () -> Unit,
    onMake: () -> Unit,
    onMoreLikeThis: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Open") }, onClick = { onDismiss(); onOpen() })
        DropdownMenuItem(text = { Text("More like this") }, onClick = { onDismiss(); onMoreLikeThis() })
        DropdownMenuItem(text = { Text("Make this a workout") }, onClick = { onDismiss(); onMake() })
        if (downloads.directOn) {
            when {
                downloads.running[video.id] is DownloadState.Running ->
                    DropdownMenuItem(text = { Text("Cancel download") }, onClick = { onDismiss(); downloads.onCancel(video.id) })
                video.id in downloads.done -> DropdownMenuItem(text = { Text("Downloaded") }, onClick = {}, enabled = false)
                else -> DropdownMenuItem(text = { Text("Download to this phone") }, onClick = { onDismiss(); downloads.onDownload(video) })
            }
        }
        DropdownMenuItem(text = { Text("Remove from saved") }, onClick = { onDismiss(); onRemove() })
    }
}

/** The full Saved list ("See all"). */
private fun androidx.compose.foundation.lazy.LazyListScope.savedAllItems(
    downloads: SavedDownloads,
    saved: List<VideoMeta>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onMake: (String) -> Unit,
    onMoreLikeThis: (VideoMeta) -> Unit,
    onRemove: (String) -> Unit,
) {
    item(key = "saved-all-head") {
        SectionLine("Saved · ${saved.size}", trailing = { GhostButton(onClick = onBack) { Text("Back") } })
    }
    if (saved.isEmpty()) {
        item(key = "saved-all-empty") { EmptyState(body = "Videos you save to watch later appear here.") }
    } else {
        item(key = "saved-all-list") {
            ListCard {
                Column {
                    saved.forEachIndexed { i, v ->
                        SavedRow(v, downloads, onOpen = { onOpen(v.id) }, onMake = { onMake(v.id) }, onMoreLikeThis = { onMoreLikeThis(v) }, onRemove = { onRemove(v.id) })
                        if (i != saved.lastIndex) ListDivider()
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
    onMakeWorkout: (String) -> Unit,
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
                SectionLine(
                    "Recommended for you",
                    note = if (rec.personalised) "From your muscle volume" else "Popular picks",
                )
            }
            if (rec.rows.isEmpty()) {
                item(key = "rec-empty") {
                    EmptyState(body = "Nothing recommended right now. Search above, or turn off \"Workouts only\" for a wider net.")
                }
            }
            rec.rows.forEachIndexed { index, row ->
                item(key = "rec-$index") {
                    val rowState = rememberLazyListState()
                    // Horizontal scroll to the end of a row loads that row's next page (a few items before the end).
                    LaunchedEffect(rowState, index) {
                        snapshotFlow { (rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to rowState.layoutInfo.totalItemsCount }
                            .distinctUntilChanged()
                            .collect { (last, total) -> if (VideoPaging.nearEnd(last, total, ahead = 2)) viewModel.loadMoreRecommended(index) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.reason, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(row.videos, key = { it.video.id }) { v -> VideoCard(v, onClick = { onOpenVideo(v.video.id) }, onMake = { onMakeWorkout(v.video.id) }) }
                            if (row.loadingMore) {
                                item(key = "more") {
                                    Box(Modifier.width(48.dp).height(135.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit, showCheck: Boolean = true) {
    IntakeChip(
        text = label,
        tone = if (selected) ChipTone.Ink else ChipTone.Neutral,
        icon = if (selected && showCheck) IntakeIcons.Check else null,
        onClick = onClick,
    )
}

/** The badge a card wears: Workout, Maybe, or how many chapters it has. */
@Composable
private fun Badges(v: ScoredVideo) {
    val chapters = VideoBrowse.chapterCount(v.video)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (v.verdict.label) {
            VideoLabel.WORKOUT -> IntakeChip("Workout", tone = ChipTone.Ok, small = true)
            VideoLabel.MAYBE -> IntakeChip("Maybe", tone = ChipTone.Warn, small = true)
            VideoLabel.NOT_WORKOUT -> IntakeChip("Not a workout", small = true)
        }
        if (chapters >= 2) IntakeChip("$chapters chapters", small = true)
    }
}

@Composable
private fun VideoRow(v: ScoredVideo, onClick: () -> Unit, onMake: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open ${v.video.title}", onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoThumbnail(v.video.thumbnailUrl, v.video.durationSec, modifier = Modifier.width(156.dp), shape = IntakeShape.Tile, showPlay = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(v.video.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, color = MaterialTheme.colorScheme.onSurface)
            Text(v.video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Badges(v)
            com.zeroindex.intake.ui.components.SoftButton(onClick = onMake, small = true) { Text("Make this a workout") }
        }
    }
}

@Composable
private fun VideoCard(v: ScoredVideo, onClick: () -> Unit, onMake: () -> Unit) {
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clickable(role = Role.Button, onClickLabel = "Open ${v.video.title}", onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VideoThumbnail(v.video.thumbnailUrl, v.video.durationSec, modifier = Modifier.fillMaxWidth())
        // Two lines always, so every card in a row is the same height.
        Text(v.video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        Text(v.video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Badges(v)
        com.zeroindex.intake.ui.components.SoftButton(onClick = onMake, small = true) { Text("Make this a workout") }
    }
}

@Composable
private fun SavedRow(v: VideoMeta, downloads: SavedDownloads, onOpen: () -> Unit, onMake: () -> Unit, onMoreLikeThis: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(112.dp).clickable(role = Role.Button, onClickLabel = "Open ${v.title}", onClick = onOpen)) {
            VideoThumbnail(v.thumbnailUrl ?: YoutubeIds.thumbnailUrl(v.id), v.durationSec, shape = IntakeShape.ExtraSmall, showPlay = false)
        }
        Column(Modifier.weight(1f)) {
            Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
            when (val d = downloads.running[v.id]) {
                is DownloadState.Running -> Text(DirectPlayback.progressText(d.percent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                is DownloadState.Failed -> Text(d.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                null -> if (v.id in downloads.done) Text("Downloaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onMake, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Make this a workout", style = MaterialTheme.typography.titleSmall)
            }
        }
        Column {
            IconButton(onClick = { menu = true }) { Icon(IntakeIcons.More, contentDescription = "More for ${v.title}", modifier = Modifier.size(IconSize.Default)) }
            SavedMenu(menu, { menu = false }, downloads, v, onOpen = onOpen, onMake = onMake, onMoreLikeThis = onMoreLikeThis, onRemove = onRemove)
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
                "The key lets Intake search YouTube for you. It is stored encrypted on this phone and sent only to YouTube, and a free one allows about 100 searches a day. Intake downloads a video only if you turn on Offline downloads and direct playback in Settings > Videos (off by default); otherwise it plays through YouTube's own player.",
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
                KeySteps()
            }
        }
        IntakeCard(Modifier.scrollIntoViewOnFocus()) {
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
            LineButton(
                onClick = onOpenLink,
                enabled = link.isNotBlank() && !linkBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (linkBusy) "Looking it up" else "Open this video") }
        }
    }
}


/** Every step to a YouTube key, in order, and how to lock the key down afterwards. Also shown under Settings > API keys. */
@Composable
fun KeySteps(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.zeroindex.intake.domain.videos.YoutubeKeyGuide.SECTIONS.forEach { section ->
            Text(section.title, style = MaterialTheme.typography.titleSmall)
            section.steps.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            section.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** The way into Downloaded videos: its count, and how much is stored. Shown when the downloads setting is on or any exist. */
@Composable
private fun DownloadsEntry(count: Int, onClick: () -> Unit) {
    ListCard {
        TrainRow(
            title = "Downloaded videos",
            subtitle = if (count == 0) "None yet. Offline downloads are on." else "$count on this phone, playable offline",
            icon = IntakeIcons.Download,
            onClick = onClick,
            onClickLabel = "Open downloaded videos",
            trailing = { RowChevronIcon() },
        )
    }
}

/** What the saved lists need to show and start downloads. Downloads only appear with the opt-in on. */
private class SavedDownloads(
    val directOn: Boolean,
    val done: Set<String>,
    val running: Map<String, DownloadState>,
    val onDownload: (VideoMeta) -> Unit,
    val onCancel: (String) -> Unit,
)

/** The Downloaded screen: every download with its size, the storage used, and Delete (file and row). Tap to play offline. */
private fun androidx.compose.foundation.lazy.LazyListScope.downloadedItems(
    items: List<DownloadedVideo>,
    usedBytes: Long,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (DownloadedVideo) -> Unit,
) {
    item(key = "dl-head") {
        Column {
            SectionLine("Downloaded · ${items.size}", trailing = { GhostButton(onClick = onBack) { Text("Back") } })
            Text(
                "${DirectPlayback.size(usedBytes)} used on this phone. These files are in Intake's private storage, play offline, and are never uploaded or backed up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (items.isEmpty()) {
        item(key = "dl-empty") { EmptyState(body = "Nothing downloaded yet. Use Download on a video's page or in the menu of a saved video.") }
    }
    items(items, key = { "dl-${it.id}" }) { d ->
        var confirm by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Play ${d.title}") { onOpen(d.videoId) },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoThumbnail(d.thumbnailUrl ?: YoutubeIds.thumbnailUrl(d.videoId), d.durationSec ?: 0, modifier = Modifier.width(120.dp), shape = IntakeShape.ExtraSmall, showPlay = false)
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(DirectPlayback.size(d.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { confirm = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete") }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("Delete this download?") },
                text = { Text("The file on this phone (${DirectPlayback.size(d.sizeBytes)}) is removed. The video stays saved if you saved it.") },
                confirmButton = { TextButton(onClick = { confirm = false; onDelete(d) }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
            )
        }
    }
}
