# Feedback round 3: first look on a real phone

2026-11-11. The owner installed the preview build (`com.zeroindex.intake.preview`) on their phone and sent screenshots.
This is the first time the Option A build was seen on a device. Each item is what they said, what was found, and what
was done. "Mine" marks an interpretation the owner did not state.

## Training

| # | What they said | What was found | What was done |
| --- | --- | --- | --- |
| 1 | The generated plan page is "a lot of small words" that most people will not read. | Every exercise carried three paragraphs in the smallest type, above a long explanation. | The preview shows the name, three numbers (days, minutes, easy-week cadence) and each day as a short list: picture, name, sets and reps, starting weight. The reasons open on a tap; the plan-level explanations are closed cards under the days. |
| 2 | It shows kg although pounds were chosen. | Starting weights, progression steps ("+2.5 kg"), plate lists and next-time notes are written in kg by the engines. | `WeightText` rewrites them for display in the person's unit. Dumbbell and machine steps are 5 lb in a pound gym. |
| 3 | With some experience, it should ask for maxes on a few key lifts and base the numbers on them. | Starting weights came from body weight and experience only, unless the lift had logged sets. | The experience question asks, for Some and Experienced, for a weight and reps on bench press, back squat, deadlift and overhead press (optional). They stand in for history, so those lifts and the lifts related to them start from what the person can do. *Mine: asked in the program generator, where experience is chosen, not in the welcome flow.* |
| 4 | Does the app act on "deload every 6 weeks", or is it only a suggestion? | It was only text. The app had a per-lift deload after missed reps, and the coach could suggest a deload week, but nothing ran on the plan's cadence. | The cadence is stored on the program. When a week that is a multiple of it begins, the next 7 days are 10% lighter with one set fewer, by itself. The plan text now says exactly that. |
| 5 | The program view's text "looks funny" and is too small and crowded. | Each row's text sat inside a pill-shaped button, which clipped its corners, beside three icon buttons. | Plain rows with larger type; the row opens the editor; about, move up and move down are in one menu; the long note is two lines. |
| 6 | After starting a workout by accident and discarding it, today's workout "moves on to the next workout". | Discard does not move the pointer. The card offered the day *scheduled for today's weekday*, so a program made on a Wednesday opened on day 2. | Weekdays now say when to train and the rotation says which workout. The pointer moves only when a workout is finished. After today's workout the card says what is next and when. *Mine: this changes the earlier rule "the day with today's weekday is today's workout".* |

## Videos

| # | What they said | What was found | What was done |
| --- | --- | --- | --- |
| 7 | A pasted link got its title but no thumbnail. | The player area was a black box: the player frame was refused when it moved between YouTube's own hosts. The middle of three buttons (Open in YouTube) had no room for its label. | The page shows the video's picture with a play button and loads YouTube's player on a tap. The picture comes from the video id, so it needs no key. The frame may use YouTube's and Google's hosts. Open in YouTube is its own labelled button. **Playback itself has not been seen working; check it on the phone.** |
| 8 | The key instructions need every step, and should say whether to restrict the key and to what. | Four short lines. | A full guide in three parts (make the key, restrict it, put it in Intake), on Videos and under Settings > API keys. Advice: restrict the key to "YouTube Data API v3" under API restrictions; leave Application restrictions on None, because "Android apps" ties the key to one exact build. |
| 9 | Shorts are not filtered; this should be a toggle like Workouts only. | No Shorts rule. | "Hide Shorts": a setting (on by default) and a chip for one visit. A Short is a video of a minute or less, or one tagged #shorts; YouTube's API has no field for it. |
| 10 | "Make this a workout" does not read what is *in* the video, only what is around it. | True, and it cannot: YouTube gives an app the title and description only. Captions are available through the API to the video's owner alone. | The screen says so, and has a box to paste the video's transcript (YouTube > description > Show transcript) or a typed list. It is read on the phone, and is what "Improve with AI" is sent if tapped. |

## Food and drinks

Handled on a parallel branch; see `RESUME.md` for what landed.

| # | What they said |
| --- | --- |
| 11 | Only the USDA row shows an allergen chip in search; every row should. |
| 12 | Products in other languages still show up in search. |
| 13 | Language choice and the other-languages switch should be in onboarding too. |
| 14 | Drinks should come from the same sources as food, with a search bar under the predefined options, showing only drinks. Food search should not show drinks, and the other way round. |
| 15 | How would a restaurant's full menu (McDonald's) look if pulled in from a link? (A question.) |

## Not yet seen after these changes

Everything in this round was verified by unit tests and a compile. The new plan preview, program rows, video page and
Shorts chip have not been seen on a device.
