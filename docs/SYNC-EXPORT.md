# Sync and export

Intake keeps everything on the phone. There are two ways to get a copy off it, both in **Settings > Sync and export**.

1. **Export to a file.** One JSON file with every row of every table, soft-deleted rows included. With a passphrase
   typed for that file it is encrypted (AES-256-GCM, key from PBKDF2-SHA256); without one it is plain JSON. There are
   also three CSV spreadsheets (food log, workouts, body metrics). Import shows what will change before writing
   anything and never deletes. The passphrase is asked for each export and **never stored**.
2. **Sync with your own server** (optional). The self-hosted `server/` pairs with the phone and carries rows between your
   devices. See `server/README.md`.

**The remote WebDAV, SFTP and SMB backup is gone** (device feedback round 2). There is no destination, no schedule and
no stored backup passphrase. On the first launch after the update the app cancels the old nightly job and clears the
credentials it stored (`BackupCredentialStore.clearDestinationSecrets`). The sync device token stays. An old `.intake`
file made by the remote backup still imports: the decrypt path in `BackupService` is unchanged.

**A monthly "export now" reminder** appears on Today when the last export is 30 days old (or, with no export yet, 30 days
after first use), **only while sync is not paired**, and only if the switch in Settings > Reminders is on. The rule is
`domain/settings/ExportReminderRule.kt`.

If you do not use sync, a file export is your backup. If you do, the server holds a copy: back up the server's database file
too (see `server/README.md`).

## Files are not in a JSON export

Backups, exports and sync carry table rows and settings, never files. That includes:

* the photo of a custom exercise (`exercise.photoPath`): after a restore the exercise is back, without the photo;
* **progress photos**: the `progress_photo` table (date, angle, file name) is exported and synced as **metadata only**.
  The JSON format cannot carry image files, so they are **not in the JSON export**. If you turn on *Include progress
  photos in the export* (off by default) a second button, *Save photo files (zip)*, writes the image files into a zip.
  The images are never synced or uploaded. On another device a synced photo row shows a placeholder.

## Settings travel with exports

The JSON export carries a `settings` section (`"settings": {"version": 1, "values": {...}}`), so a reinstall (export,
uninstall, install, import) keeps your preferences. Each value is stored with its type. The import preview shows a line
such as "Settings: units, targets, profile, water, coach".

**Included:** units (weight, length, volume, food portions), theme, Today card switches, display name, rest timer and
plates, goal weight and rate, protein and fat per kg, the auto-target and add-active-calories switches, the plan week, the
profile (including equipment and the Advanced nutrition choices), the baseline marker, plan history and week pattern,
hydration settings (the weather opt-in, chosen place name, drink edits and measured sweat rate), the coach settings and
state, AI privacy, budget and per-task model choices, the daily-insight switch, the food database switches, Videos
switches, sodium display, the eat-out radius, jump tests, weekly set targets, reminders (photo, export) and the
include-photos switch.

**Never included:** API keys (OpenRouter, USDA, YouTube) and the Open Food Facts login (encrypted preferences, not in
DataStore at all), the sync server, device id and cursors, "seen" tips and tour flags, the Health Connect watermarks, last
export time, the first-use time, and the weather coordinates, cached forecast and retry time.

The rule lives in `domain/backup/PortableSettings.kt`: an allow-list plus a deny-list that refuses any name containing
key, token, secret, password, device, sync_ and similar, even under an allowed prefix. A test reads every preference name
in the three stores and fails if a new one has not been deliberately included or excluded. **Settings are not part of
device sync**; they travel only with exports.

## Restoring

Settings > Sync and export > Import a backup, pick the `.intake` (or `.json`) file, enter the passphrase if it asks.
