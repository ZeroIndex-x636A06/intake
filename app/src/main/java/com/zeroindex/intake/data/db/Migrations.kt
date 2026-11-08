package com.zeroindex.intake.data.db

import androidx.room.migration.Migration

/**
 * SPEC §4: an explicit migration for every schema change from version 1
 * forward, and `fallbackToDestructiveMigration` is never used - not even in
 * debug. This is a personal dataset whose only backups are the user's own
 * exports (SPEC §3.8), so a destructive migration means losing real history.
 *
 * Adding a migration:
 *  1. bump [IntakeDatabase.VERSION];
 *  2. add a `Migration(n, n + 1)` below and append it to [ALL];
 *  3. build once so Room writes the new `schemas/<version>.json`, and commit it;
 *  4. add a migration test against the committed schema JSON.
 *
 * Version 1 is the initial schema, so there is nothing to migrate from yet.
 */
internal val ALL: Array<Migration> = emptyArray()
