# Videos (Train > Videos)

Videos is the third section of Train. It uses **the person's own YouTube Data API key** (Settings > API keys), as decided in `docs/FEEDBACK-R2.md`. Intake never downloads, rehosts or stores a video; playback is YouTube's own embedded player in a WebView.

## What it does
- **Search** (key needed): `search.list` with `type=video`, `videoEmbeddable=true`, `safeSearch=moderate`, then `videos.list` with `part=snippet,contentDetails,statistics`. Runs only when the person submits or taps a style chip. The key goes only in the `key` query parameter the API requires and is never logged. A free key allows about 100 searches a day (a search is 100 units of 10,000; the screen shows a local estimate).
- **Workout filter** (`domain/videos/WorkoutVideoClassifier`): a deterministic score from category (17, 26), title and description words, negative words, length and channel. The default comes from Settings > Videos; the chip changes it for the visit; "Show everything" lifts it for the current search only and never changes a score. Length chips filter on the phone.
- **Recommended**: up to 3 searches built from this week's under-target regions, equipment, the program name and saved titles (`RecommendedQueries`); fixed generic words when "Personalise Recommended" is off. Each row shows its reason. No model is involved.
- **No key**: the section explains the key, points to Settings > API keys and offers **Paste a YouTube link** (oEmbed gives the title only).
- **Saved**: watch later, soft-deleted when removed.
- **Video page**: player, the filter's verdict in words, Make this a workout, Save, Open in YouTube, Share link, chapters (a tap restarts the player there), description.
- **Make this a workout**: chapters, then lines of the description, are read on the phone and fuzzy-matched to the exercise library (`VideoWorkoutDrafter`). The person reviews and edits every row (include, rename, pick another exercise, sets, reps or seconds, rest), then saves a My workout linked to the video, optionally starting it. **Improve with AI** is a separate tap through `AiGateway` (`AiFeature.VIDEO_TO_WORKOUT`, `AiTrigger.USER_ACTION`), shown only when AI is on and a key exists; its strict-schema answer only replaces the draft.

## Data and the 30-day rule
`video_cache` and `video_search` (v3 `migrateVideos_2_3`) hold API results: search results are reused for 6 hours, one video's details for a day, and everything is deleted 30 days after fetching (`VideoCachePolicy`, `VideoRepository.purgeExpired`, run when Videos opens). They are not in backups, exports or sync. `saved_video` is the person's data; its API snapshot columns are cleared at 30 days. Thumbnails are held in memory only. "Clear cached video information" empties the cache at once.

## Limits
Fullscreen is off in the embedded player (`fs=0`). Offline, saved titles still show and the player does not. A pasted link without a key has no description, so there are no chapters to read.
