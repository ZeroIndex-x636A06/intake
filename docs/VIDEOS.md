# Videos (Train > Videos)

Videos is the third section of Train. It uses **the person's own YouTube Data API key** (Settings > API keys), as decided in `docs/FEEDBACK-R2.md`. Intake never downloads, rehosts or stores a video; playback is YouTube's own embedded player in a WebView.

## What it does
- **Search** (key needed): `search.list` with `type=video`, `videoEmbeddable=true`, `safeSearch=moderate`, then `videos.list` with `part=snippet,contentDetails,statistics`. Runs only when the person submits or taps a style chip. The key goes only in the `key` query parameter the API requires and is never logged. A free key allows about 100 searches a day (a search is 100 units of 10,000; the overflow menu shows a local estimate).
- **Workout filter** (`domain/videos/WorkoutVideoClassifier`): a deterministic score from category (17, 26), title and description words, negative words, length and channel. The default comes from Settings > Videos; the chip changes it for the visit; "Show everything" lifts it for the current search only and never changes a score. Length chips filter on the phone.
- **Recommended**: up to 3 searches built from this week's under-target regions, equipment, the program name and saved titles (`RecommendedQueries`); fixed generic words when "Personalise Recommended" is off. Each row shows its reason. No model is involved.
- **No key**: the section explains the key, points to Settings > API keys and offers **Paste a YouTube link** (oEmbed gives the title only).
- **Saved**: watch later, soft-deleted when removed.
- **Video page**: player, the filter's verdict in words, Make this a workout, Save, Open in YouTube, Share link, chapters (a tap restarts the player there), description.
- **Make this a workout**: chapters, then lines of the description, are read on the phone and fuzzy-matched to the exercise library (`VideoWorkoutDrafter`). The person reviews and edits every row (include, rename, pick another exercise, sets, reps or seconds, rest), then saves a My workout linked to the video, optionally starting it. **Improve with AI** is a separate tap through `AiGateway` (`AiFeature.VIDEO_TO_WORKOUT`, `AiTrigger.USER_ACTION`), shown only when AI is on and a key exists; its strict-schema answer only replaces the draft.

## The Videos screen (round 5, item 14)
Top to bottom: the search box with an overflow menu; one filter row (**Workouts only**, **Hide Shorts**, **Filters** with a count); **Saved**; then Recommended or the search results.
- **Filters** opens a sheet with muscle group, style and duration. The count is how many of those three are set. Muscle group and style add words to the search (`VideoBrowse.muscleWord`, so Apply costs one search when either changed); duration only hides videos on the phone.
- **Saved: a compact row, not a segmented control.** "Saved · N" with "See all" sits right under the filters, so it is always reachable and is never pushed down by results. A "For you / Saved / Search results" segmented control was the other option; it was not chosen because it hides Recommended and the results behind a tap, and the person would no longer see their saved videos next to what they are searching for. "See all" opens the full list (back returns).
- **Overflow menu**: "About N searches left today", "How Videos works" (the key and privacy note) and "Clear cached video information". None of these is on the main screen any more.
- **Recommended**: at most 3 rows (`RecommendedQueries.MAX_QUERIES`). Every card is 240 dp wide with a title of exactly 2 lines (ellipsis), so rows are even.
- **More like this** (the menu of a saved video, in the row and in See all) runs a search from that video's channel and title words.

## Paging and quota
- A search loads the next page (25 videos) with YouTube's `nextPageToken` when the last visible item is within 5 of the end (`VideoPaging.nearEnd`), shows a spinner, and says "That is all the results." at the end. A Recommended row loads its next page when it is scrolled to within 2 cards of its end.
- Each page costs one `search.list` (100 units) and one `videos.list` (1 unit): about 101 units, or nothing from the cache. Caps: 5 pages for a search (125 results, about 505 units) and 3 pages for a Recommended row. Paging also stops while the local estimate shows 5 or fewer searches left (`VideoPaging.RESERVED_SEARCHES`), so a typed search still works, and the screen says why. The estimate is shown only in the overflow menu.
- Pages are cached under the same rules as searches: six hours, deleted at 30 days, not in backups. Page 1 keeps the plain search key; page N is `key|pageN`, and the token that leads from page N is a row `key|nextN` whose ids column holds the token (blank at the end). This reuses `video_search`, so there is no schema change (still v5). A page is only requested after the page before it, only because the person scrolled. A search cached before this change has no token row, so it is fetched once more.
- The key still goes only in the `key` query parameter; the page token is not secret and is the only extra parameter.

## Hide Shorts
YouTube's API has no Short flag and Shorts can now be 3 minutes long, so the old "60 seconds or less" rule let "The COMPLETE Back Workout (under 1 minute)" (1:02) through. With Hide Shorts on (`ShortsRule`), a video is a Short if ANY of these is true:
1. its length is 1 to 60 seconds;
2. its title, or the first 600 characters of its description, has `#shorts`, `#short`, `#ytshorts`, `#shortsvideo` or `#shortsfeed` (at any length);
3. its length is 1 to 180 seconds AND its title or description says so: "YouTube Shorts", "under/in/within/less than/just N seconds" (N up to 180), "under/in a/one/two/three/1-3 minutes", or "N-second" / "N sec" with N up to 180.
A video with no known length (a pasted link) is never a Short by length or words, only by the hashtag. "Shortstop drills" and "rest 90 seconds between sets" in a 30-minute video are not Shorts.
A vertical-format signal (`part=player`, `embedHeight > embedWidth`) is **not used**: it needs another API part cached for each video (a new column, so schema v6) and the embed size is not a documented statement of orientation. Revisit if the rules above miss too many.
Tests: `ShortsAndPagingTest` (the 1:02 example and the edge cases above).

## Similar to what you saved
Recommended's "Because you saved ..." row is built from the saved videos' channels and title keywords (`SavedSimilarity`): the most common channel, the two most common non-filler title words, and "workout". The reason names the style when a saved title is a known style (pilates, yoga ...), else the channel. "More like this" uses the same builder for one video.

## Data and the 30-day rule
`video_cache` and `video_search` (v3 `migrateVideos_2_3`) hold API results: search results are reused for 6 hours, one video's details for a day, and everything is deleted 30 days after fetching (`VideoCachePolicy`, `VideoRepository.purgeExpired`, run when Videos opens). They are not in backups, exports or sync. `saved_video` is the person's data; its API snapshot columns are cleared at 30 days. Thumbnails are held in memory only. "Clear cached video information" empties the cache at once.

## Limits
Fullscreen is off in the embedded player (`fs=0`). Offline, saved titles still show and the player does not. A pasted link without a key has no description, so there are no chapters to read.
