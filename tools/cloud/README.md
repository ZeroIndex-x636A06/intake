# Cloud container helpers

Only for building and testing in a cloud container. Nothing here ships in the app. See `docs/HANDOFF.md` section 4.

- `mvn-mirror.py`: a caching mirror of Maven Central on `127.0.0.1:8099` that fetches with curl. Gradle's own client
  gets HTTP 429 from Maven Central in these containers. Run it in the background: `python3 tools/cloud/mvn-mirror.py &`.
- `mirror.gradle.kts`: copy to `~/.gradle/init.d/`. It points every Maven Central repository at the mirror.
- `emu-ui.py`: drives an emulator over adb: `dump` (visible text with tap coordinates), `tap "text"`, `tapxy x y`,
  `text "..."`, `back`, `swipe up|down`, `shot name` (a half-size screenshot into `$EMU_SHOTS`), `crash`.
