package com.zeroindex.intake.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SPEC §4: an explicit migration for every schema change from version 1
 * forward, and `fallbackToDestructiveMigration` is never used - not even in
 * debug. This is a personal dataset whose only backups are the user's own
 * exports (SPEC §3.8), so a destructive migration means losing real history.
 *
 * Adding a migration:
 *  1. bump [IntakeDatabase.VERSION];
 *  2. add a statement list and a `Migration(n, n + 1)` below, and append it to
 *     [ALL];
 *  3. build once so Room writes the new `schemas/<version>.json`, and commit it;
 *  4. extend `MigrationSchemaTest`, which runs the statements against the
 *     committed schema JSON and compares the result to what Room expects.
 *
 * Each migration's SQL is a plain list so that test executes exactly what a
 * phone would, not a copy of it.
 */

/**
 * 1 -> 2: the SPEC amendments. Purely additive - nine new tables and new
 * nullable or defaulted columns on five existing ones - so no existing row is
 * rewritten and nothing can be lost.
 */
internal val MIGRATION_1_2_STATEMENTS: List<String> = listOf(
    // Recipe Hunter provenance and cached per-serving macros.
    "ALTER TABLE `recipe` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'USER'",
    "ALTER TABLE `recipe` ADD COLUMN `sourceUrl` TEXT",
    "ALTER TABLE `recipe` ADD COLUMN `ingredientsText` TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE `recipe` ADD COLUMN `kcalPerServing` REAL",
    "ALTER TABLE `recipe` ADD COLUMN `proteinPerServing` REAL",
    "ALTER TABLE `recipe` ADD COLUMN `carbsPerServing` REAL",
    "ALTER TABLE `recipe` ADD COLUMN `fatPerServing` REAL",
    "ALTER TABLE `recipe` ADD COLUMN `allergenStatus` TEXT",
    // NFC meal prep tags.
    "ALTER TABLE `meal_template` ADD COLUMN `nfcTagUid` TEXT",
    "ALTER TABLE `meal_template` ADD COLUMN `nfcWrittenAt` INTEGER",
    "ALTER TABLE `meal_template` ADD COLUMN `lastLoggedAt` INTEGER",
    // Body composition from Health Connect.
    "ALTER TABLE `body_metric` ADD COLUMN `skeletalMuscleKg` REAL",
    "ALTER TABLE `body_metric` ADD COLUMN `leanBodyMassKg` REAL",
    "ALTER TABLE `body_metric` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MANUAL'",
    // Plan B results saved for offline use.
    "ALTER TABLE `eat_out_option` ADD COLUMN `estimatedCarbs` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `eat_out_option` ADD COLUMN `estimatedFat` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `eat_out_option` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'PLAN'",
    "ALTER TABLE `eat_out_option` ADD COLUMN `address` TEXT",
    "ALTER TABLE `eat_out_option` ADD COLUMN `allergenStatus` TEXT",

    "CREATE TABLE IF NOT EXISTS `hydration_log` (`id` TEXT NOT NULL, `loggedAt` INTEGER NOT NULL, `volumeMl` REAL NOT NULL, `presetId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_hydration_log_loggedAt` ON `hydration_log` (`loggedAt`)",
    "CREATE INDEX IF NOT EXISTS `index_hydration_log_deletedAt` ON `hydration_log` (`deletedAt`)",

    "CREATE TABLE IF NOT EXISTS `hydration_preset` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `volumeMl` REAL NOT NULL, `displayOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_hydration_preset_deletedAt` ON `hydration_preset` (`deletedAt`)",

    "CREATE TABLE IF NOT EXISTS `performance_metric` (`id` TEXT NOT NULL, `measuredAt` INTEGER NOT NULL, `verticalJumpCm` REAL, `standingReachCm` REAL, `totalReachCm` REAL, `notes` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_performance_metric_measuredAt` ON `performance_metric` (`measuredAt`)",
    "CREATE INDEX IF NOT EXISTS `index_performance_metric_deletedAt` ON `performance_metric` (`deletedAt`)",

    "CREATE TABLE IF NOT EXISTS `menu_source` (`id` TEXT NOT NULL, `restaurantName` TEXT NOT NULL, `kind` TEXT NOT NULL, `location` TEXT NOT NULL, `pastedText` TEXT, `enabled` INTEGER NOT NULL, `lastRunAt` INTEGER, `lastStatus` TEXT NOT NULL, `lastError` TEXT, `itemCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_menu_source_deletedAt` ON `menu_source` (`deletedAt`)",

    "CREATE TABLE IF NOT EXISTS `menu_item` (`id` TEXT NOT NULL, `sourceId` TEXT, `restaurantName` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `ingredientsText` TEXT NOT NULL, `kcal` REAL, `proteinG` REAL, `carbsG` REAL, `fatG` REAL, `macrosPublished` INTEGER NOT NULL, `allergenStatus` TEXT NOT NULL, `allergenMatches` TEXT NOT NULL, `priceCents` INTEGER, `importedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`sourceId`) REFERENCES `menu_source`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE INDEX IF NOT EXISTS `index_menu_item_sourceId` ON `menu_item` (`sourceId`)",
    "CREATE INDEX IF NOT EXISTS `index_menu_item_restaurantName` ON `menu_item` (`restaurantName`)",
    "CREATE INDEX IF NOT EXISTS `index_menu_item_deletedAt` ON `menu_item` (`deletedAt`)",

    "CREATE TABLE IF NOT EXISTS `ai_insight` (`id` TEXT NOT NULL, `dayStartMillis` INTEGER NOT NULL, `text` TEXT NOT NULL, `rationale` TEXT NOT NULL, `model` TEXT NOT NULL, `dismissedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_ai_insight_dayStartMillis` ON `ai_insight` (`dayStartMillis`)",
    "CREATE INDEX IF NOT EXISTS `index_ai_insight_deletedAt` ON `ai_insight` (`deletedAt`)",

    "CREATE TABLE IF NOT EXISTS `model_benchmark` (`id` TEXT NOT NULL, `task` TEXT NOT NULL, `modelId` TEXT NOT NULL, `runAt` INTEGER NOT NULL, `fixtures` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `jsonValidity` REAL NOT NULL, `medianLatencyMs` INTEGER NOT NULL, `costUsdPerRequest` REAL NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_model_benchmark_task` ON `model_benchmark` (`task`)",
    "CREATE INDEX IF NOT EXISTS `index_model_benchmark_modelId` ON `model_benchmark` (`modelId`)",
    "CREATE INDEX IF NOT EXISTS `index_model_benchmark_deletedAt` ON `model_benchmark` (`deletedAt`)",

    "CREATE TABLE IF NOT EXISTS `health_daily_summary` (`id` TEXT NOT NULL, `dayStartMillis` INTEGER NOT NULL, `activeKcalRaw` REAL, `bmrKcalInWindows` REAL, `activeKcalNet` REAL, `totalKcalBurned` REAL, `steps` INTEGER, `sleepMinutes` INTEGER, `sleepStartMillis` INTEGER, `restingHeartRate` REAL, `avgHeartRate` REAL, `syncedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_health_daily_summary_dayStartMillis` ON `health_daily_summary` (`dayStartMillis`)",
    "CREATE INDEX IF NOT EXISTS `index_health_daily_summary_deletedAt` ON `health_daily_summary` (`deletedAt`)",

    "CREATE TABLE IF NOT EXISTS `backup_run` (`id` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `destination` TEXT NOT NULL, `bytes` INTEGER, `status` TEXT NOT NULL, `error` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_backup_run_startedAt` ON `backup_run` (`startedAt`)",
    "CREATE INDEX IF NOT EXISTS `index_backup_run_deletedAt` ON `backup_run` (`deletedAt`)",
    // Starting weight from a program script, used as the first prescription (no history yet).
    "ALTER TABLE `program_exercise` ADD COLUMN `startWeightKg` REAL",
    // Sync conflicts kept for review (amendment §3).
    "CREATE TABLE IF NOT EXISTS `sync_conflict` (`id` TEXT NOT NULL, `tableName` TEXT NOT NULL, `rowId` TEXT NOT NULL, `keptJson` TEXT NOT NULL, `otherJson` TEXT NOT NULL, `otherSource` TEXT NOT NULL, `detectedAt` INTEGER NOT NULL, `resolvedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_sync_conflict_resolvedAt` ON `sync_conflict` (`resolvedAt`)",
    "CREATE INDEX IF NOT EXISTS `index_sync_conflict_tableName_rowId` ON `sync_conflict` (`tableName`, `rowId`)",
)

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_STATEMENTS.forEach(db::execSQL)
    }
}

/**
 * 2 -> 3: everything added after the v2 release (APK built at 4952404). Version 2 is FROZEN: the user's
 * installed database is exactly `schemas/.../2.json`, so every later change lands here. Purely additive.
 *
 * One small named function per feature, run in order by [MIGRATION_2_3_STATEMENTS]. Each is a plain
 * statement list so `MigrationSchemaTest` executes the same SQL a phone would.
 */
internal fun migrateFoodOff_2_3(): List<String> = listOf(
    // Open Food Facts contributions (docs/FOOD-DATA.md): when the user last sent a product to OFF.
    "ALTER TABLE `food` ADD COLUMN `offContributedAt` INTEGER",
)

internal fun migrateFoodUi_2_3(): List<String> = listOf(
    // Product thumbnails in Add food and My foods: the Open Food Facts image address, nullable. No picture is stored.
    "ALTER TABLE `food` ADD COLUMN `imageUrl` TEXT",
)

internal fun migrateAllergens_2_3(): List<String> = listOf(
    // Allergens rebuild: a level and an off switch on every keyword, a custom flag, and the allergen list.
    // Built-in rows and keywords are seeded on every open (AllergenSeed.topUp, INSERT OR IGNORE), so v2
    // installs receive them too; existing rows keep CONTAINS.
    "ALTER TABLE `allergen_alias` ADD COLUMN `level` TEXT NOT NULL DEFAULT 'CONTAINS'",
    "ALTER TABLE `allergen_alias` ADD COLUMN `isCustom` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `allergen_alias` ADD COLUMN `isEnabled` INTEGER NOT NULL DEFAULT 1",
    // Anything that is not a seeded id was typed by the user.
    "UPDATE `allergen_alias` SET `isCustom` = 1 WHERE `id` NOT LIKE 'seed-%'",
    "CREATE TABLE IF NOT EXISTS `allergen_definition` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `displayName` TEXT NOT NULL, `description` TEXT NOT NULL DEFAULT '', `isCustom` INTEGER NOT NULL DEFAULT 0, `sortOrder` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_allergen_definition_name` ON `allergen_definition` (`name`)",
    "CREATE INDEX IF NOT EXISTS `index_allergen_definition_deletedAt` ON `allergen_definition` (`deletedAt`)",
)

internal fun migrateHydration_2_3(): List<String> = listOf(
    // Hydration v2: every drink has a type and a frozen hydration factor, caffeine and pure alcohol. Existing
    // rows become plain water (factor 1.0), so every old daily total stays exactly as it was.
    "ALTER TABLE `hydration_log` ADD COLUMN `drinkType` TEXT NOT NULL DEFAULT 'WATER'",
    "ALTER TABLE `hydration_log` ADD COLUMN `hydrationFactor` REAL NOT NULL DEFAULT 1.0",
    "ALTER TABLE `hydration_log` ADD COLUMN `caffeineMg` REAL NOT NULL DEFAULT 0.0",
    "ALTER TABLE `hydration_log` ADD COLUMN `alcoholMl` REAL NOT NULL DEFAULT 0.0",
    "ALTER TABLE `hydration_preset` ADD COLUMN `drinkType` TEXT NOT NULL DEFAULT 'WATER'",
)

internal fun migrateCoach_2_3(): List<String> = listOf(
    // Coach engine: every weekly suggestion and what the person did with it. Purely additive.
    "CREATE TABLE IF NOT EXISTS `coach_suggestion` (`id` TEXT NOT NULL, `checkInDay` INTEGER NOT NULL, `suggestionKey` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `why` TEXT NOT NULL, `details` TEXT, `actionJson` TEXT NOT NULL, `priority` INTEGER NOT NULL, `status` TEXT NOT NULL, `decidedAt` INTEGER, `aiTitle` TEXT, `aiWhy` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_coach_suggestion_checkInDay` ON `coach_suggestion` (`checkInDay`)",
    "CREATE INDEX IF NOT EXISTS `index_coach_suggestion_status` ON `coach_suggestion` (`status`)",
    "CREATE INDEX IF NOT EXISTS `index_coach_suggestion_deletedAt` ON `coach_suggestion` (`deletedAt`)",
)

internal fun migrateAiUsage_2_3(): List<String> = listOf(
    // AI usage meter and monthly budget: one row per model call (feature, model, tokens, cost). Per device,
    // so it is not in backups or sync. Purely additive.
    "CREATE TABLE IF NOT EXISTS `ai_usage` (`id` TEXT NOT NULL, `feature` TEXT NOT NULL, `model` TEXT NOT NULL, `triggerKind` TEXT NOT NULL, `promptTokens` INTEGER, `completionTokens` INTEGER, `costUsd` REAL NOT NULL, `costReported` INTEGER NOT NULL, `success` INTEGER NOT NULL, `at` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_ai_usage_at` ON `ai_usage` (`at`)",
    "CREATE INDEX IF NOT EXISTS `index_ai_usage_feature` ON `ai_usage` (`feature`)",
    "CREATE INDEX IF NOT EXISTS `index_ai_usage_deletedAt` ON `ai_usage` (`deletedAt`)",
)

internal fun migrateAiFailureKind_2_3(): List<String> = listOf(
    // Why an AI call failed (timeout, unreadable answer, refused, rate limited, ...), for the usage meter. Nullable,
    // so every existing row keeps its meaning. Purely additive.
    "ALTER TABLE `ai_usage` ADD COLUMN `failureKind` TEXT",
)

internal fun migrateExercises_2_3(): List<String> = listOf(
    // Exercise diagrams, custom exercises and custom workouts (docs/EXERCISE-IMAGES.md, docs/WORKOUTS.md).
    // The person's own exercise photo (a file name in app storage), a saved single-session workout is a
    // program with isStandalone = 1, an optional scheduled weekday on a program day, and a note per
    // exercise in a routine. Purely additive: every existing row keeps its meaning.
    "ALTER TABLE `exercise` ADD COLUMN `photoPath` TEXT",
    "ALTER TABLE `program` ADD COLUMN `isStandalone` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `program_day` ADD COLUMN `weekday` INTEGER",
    "ALTER TABLE `program_exercise` ADD COLUMN `notes` TEXT",
)

internal fun migrateRecipes_2_3(): List<String> = listOf(
    // Recipe import (docs/RECIPE-IMPORT.md): the Notes section, prep and cook time, the photo's address, the
    // page's fibre, sugar, sodium (mg) and saturated fat per serving, and ingredient groups ("For the Sauce").
    // A food-backed ingredient keeps its group on its own row; text lines keep theirs in recipe.ingredientGroups
    // (one label per line of ingredientsText). A shopping item can say which group it is used in.
    // All nullable, so every existing row keeps its meaning.
    "ALTER TABLE `recipe` ADD COLUMN `notes` TEXT",
    "ALTER TABLE `recipe` ADD COLUMN `prepMinutes` INTEGER",
    "ALTER TABLE `recipe` ADD COLUMN `cookMinutes` INTEGER",
    "ALTER TABLE `recipe` ADD COLUMN `imageUrl` TEXT",
    "ALTER TABLE `recipe` ADD COLUMN `fiberPerServing` REAL",
    "ALTER TABLE `recipe` ADD COLUMN `sugarPerServing` REAL",
    "ALTER TABLE `recipe` ADD COLUMN `sodiumMgPerServing` REAL",
    "ALTER TABLE `recipe` ADD COLUMN `saturatedFatPerServing` REAL",
    "ALTER TABLE `recipe` ADD COLUMN `ingredientGroups` TEXT",
    "ALTER TABLE `recipe_ingredient` ADD COLUMN `groupLabel` TEXT",
    "ALTER TABLE `shopping_list_item` ADD COLUMN `note` TEXT",
)

internal fun migrateProgressPhotos_2_3(): List<String> = listOf(
    // Progress photos (You > Progress photos): metadata only. The image is a private file named fileName; it is never
    // synced or uploaded. Purely additive.
    "CREATE TABLE IF NOT EXISTS `progress_photo` (`id` TEXT NOT NULL, `takenAt` INTEGER NOT NULL, `angle` TEXT NOT NULL, `fileName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_progress_photo_takenAt` ON `progress_photo` (`takenAt`)",
    "CREATE INDEX IF NOT EXISTS `index_progress_photo_deletedAt` ON `progress_photo` (`deletedAt`)",
)

internal fun migrateDrinks_2_3(): List<String> = listOf(
    // Drinks in the food database (Nutrition > Drinks): a drink is a food with a category, caffeine, strength and a
    // hydration factor per 100 ml, and a drink log writes a hydration row and a food-log row that point at each
    // other (hydration_log.foodLogId). All nullable, so every existing row keeps its meaning.
    "ALTER TABLE `food` ADD COLUMN `drinkCategory` TEXT",
    "ALTER TABLE `food` ADD COLUMN `caffeineMgPer100ml` REAL",
    "ALTER TABLE `food` ADD COLUMN `abvPercent` REAL",
    "ALTER TABLE `food` ADD COLUMN `hydrationFactor` REAL",
    "CREATE INDEX IF NOT EXISTS `index_food_drinkCategory` ON `food` (`drinkCategory`)",
    "ALTER TABLE `hydration_log` ADD COLUMN `foodLogId` TEXT",
    "CREATE INDEX IF NOT EXISTS `index_hydration_log_foodLogId` ON `hydration_log` (`foodLogId`)",
)

internal fun migrateNutrientsListed_2_3(): List<String> = listOf(
    // Which of fibre, sugar, sodium and saturated fat a food's source did not give, so "not listed" is not shown as 0.
    // Existing rows get the empty list: their zeros stay zeros, exactly as before.
    "ALTER TABLE `food` ADD COLUMN `unlistedNutrients` TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE `food_log` ADD COLUMN `unlistedNutrients` TEXT NOT NULL DEFAULT ''",
)

internal fun migrateHealthActive_2_3(): List<String> = listOf(
    // Health Connect: the all-day active calories for the Today card and the Progress charts. Null on every old row
    // until the next sync fills it in.
    "ALTER TABLE `health_daily_summary` ADD COLUMN `activeKcalDay` REAL",
)

internal fun migrateDeload_2_3(): List<String> = listOf(
    // A generated program's planned easy week ("deload every 6 weeks") is kept with the program, so the app can switch it
    // on by itself. Null on every existing program: nothing changes for them.
    "ALTER TABLE `program` ADD COLUMN `deloadEveryWeeks` INTEGER",
)

internal fun migrateTraining_2_3(): List<String> = listOf(
    // Train and Videos. A workout can remember the YouTube video it was made from.
    "ALTER TABLE `program` ADD COLUMN `videoId` TEXT",
    // Cache of YouTube API data (deleted after 30 days, never backed up) and the person's saved videos.
    "CREATE TABLE IF NOT EXISTS `video_cache` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `channelTitle` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `durationSec` INTEGER NOT NULL, `thumbnailUrl` TEXT, `publishedAt` TEXT NOT NULL, `viewCount` INTEGER, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))",
    "CREATE INDEX IF NOT EXISTS `index_video_cache_fetchedAt` ON `video_cache` (`fetchedAt`)",
    "CREATE TABLE IF NOT EXISTS `video_search` (`searchKey` TEXT NOT NULL, `videoIds` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`searchKey`))",
    "CREATE INDEX IF NOT EXISTS `index_video_search_fetchedAt` ON `video_search` (`fetchedAt`)",
    "CREATE TABLE IF NOT EXISTS `saved_video` (`id` TEXT NOT NULL, `videoId` TEXT NOT NULL, `title` TEXT, `channelTitle` TEXT, `categoryId` TEXT, `durationSec` INTEGER, `thumbnailUrl` TEXT, `metaFetchedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_saved_video_videoId` ON `saved_video` (`videoId`)",
    "CREATE INDEX IF NOT EXISTS `index_saved_video_deletedAt` ON `saved_video` (`deletedAt`)",
)

// Add new 2 -> 3 steps to the list below, after the existing ones.
internal val MIGRATION_2_3_STATEMENTS: List<String> =
    migrateFoodOff_2_3() +
        migrateAllergens_2_3() +
        migrateMealPlans_2_3() +
        migrateHydration_2_3() +
        migrateCoach_2_3() +
        migrateAiUsage_2_3() +
        migrateExercises_2_3() +
        migrateFoodUi_2_3() +
        migrateRecipes_2_3() +
        migrateAiFailureKind_2_3() +
        migrateProgressPhotos_2_3() +
        migrateDrinks_2_3() +
        migrateNutrientsListed_2_3() +
        migrateTraining_2_3() +
        migrateDeload_2_3() +
        migrateHealthActive_2_3()

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_2_3_STATEMENTS.forEach(db::execSQL)
    }
}

/**
 * Meal plans of any length (SPEC-v3 item 8). Kept as its own block so it can sit beside the other
 * 2 -> 3 changes. Purely additive except the plan-wide row: week 0 (which held the "Every week"
 * JSON) is copied onto the new plan and then **soft-deleted**, never removed.
 *
 * Existing weeks (weekNumber 1..N) become "My plan": active, starting today, no repeat. A database
 * with no plan weeks gets no plan, and the app asks the user to create one.
 */
internal fun migrateMealPlans_2_3(): List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `meal_plan` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `notes` TEXT NOT NULL DEFAULT '', `startDate` INTEGER NOT NULL, `loopMode` TEXT NOT NULL DEFAULT 'NONE', `loopCount` INTEGER NOT NULL DEFAULT 0, `isActive` INTEGER NOT NULL DEFAULT 0, `metaJson` TEXT NOT NULL DEFAULT '', `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_meal_plan_isActive` ON `meal_plan` (`isActive`)",
    "CREATE INDEX IF NOT EXISTS `index_meal_plan_deletedAt` ON `meal_plan` (`deletedAt`)",
    "ALTER TABLE `plan_week` ADD COLUMN `planId` TEXT NOT NULL DEFAULT ''",
    "CREATE INDEX IF NOT EXISTS `index_plan_week_planId_weekNumber` ON `plan_week` (`planId`, `weekNumber`)",
    // One plan from whatever the user already has (live rows only; start date = today's UTC epoch day).
    "INSERT INTO `meal_plan` (`id`, `name`, `notes`, `startDate`, `loopMode`, `loopCount`, `isActive`, `metaJson`, `createdAt`, `updatedAt`, `deletedAt`) " +
        "SELECT 'plan-default', 'My plan', '', CAST(strftime('%s','now') AS INTEGER) / 86400, 'NONE', 0, 1, " +
        "COALESCE((SELECT `notes` FROM `plan_week` WHERE `weekNumber` = 0 AND `deletedAt` IS NULL LIMIT 1), ''), " +
        "CAST(strftime('%s','now') AS INTEGER) * 1000, CAST(strftime('%s','now') AS INTEGER) * 1000, NULL " +
        "WHERE EXISTS (SELECT 1 FROM `plan_week` WHERE `deletedAt` IS NULL)",
    "UPDATE `plan_week` SET `planId` = 'plan-default' WHERE EXISTS (SELECT 1 FROM `meal_plan` WHERE `id` = 'plan-default')",
    // The plan-wide row has moved onto the plan: retire it (soft).
    "UPDATE `plan_week` SET `deletedAt` = CAST(strftime('%s','now') AS INTEGER) * 1000, `updatedAt` = CAST(strftime('%s','now') AS INTEGER) * 1000 " +
        "WHERE `weekNumber` = 0 AND `deletedAt` IS NULL",
)

/**
 * 3 -> 4: repairs a version-3 database that was made by an EARLIER build of version 3.
 *
 * Version 3 was edited in place while preview builds were already on the owner's phone (columns such as
 * `program.deloadEveryWeeks` and `health_daily_summary.activeKcalDay` were added to 2 -> 3 after the fact). Room only
 * compares a version number and an identity hash, so a newer build opening an older "version 3" file stops with
 * "Room cannot verify the data integrity" and the app crashes on every launch. Bumping the version gives such a file a
 * migration to run.
 *
 * The repair replays the structural half of 2 -> 3 and skips whatever the file already has: `CREATE ... IF NOT EXISTS`
 * statements run as they are, and an `ALTER TABLE ... ADD COLUMN` runs only when the column is missing ([columnsOf]
 * reads the table's current columns). The data statements of 2 -> 3 (the meal plan copy) are not replayed. On a file
 * that already has everything this does nothing, so a current version-3 file passes through it untouched. Nothing is
 * dropped or rewritten.
 */
internal fun migrateRepair_3_4(columnsOf: (table: String) -> Set<String>): List<String> {
    val addColumn = Regex("^ALTER TABLE `(\\w+)` ADD COLUMN `(\\w+)`", RegexOption.IGNORE_CASE)
    val columns = HashMap<String, Set<String>>()
    return MIGRATION_2_3_STATEMENTS.filter { sql ->
        val m = addColumn.find(sql)
        if (m != null) {
            val (table, column) = m.destructured
            val have = columns.getOrPut(table) { columnsOf(table).map { it.lowercase() }.toSet() }
            column.lowercase() !in have
        } else {
            sql.startsWith("CREATE ", ignoreCase = true)
        }
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val statements = migrateRepair_3_4 { table ->
            buildSet {
                db.query("PRAGMA table_info(`$table`)").use { c ->
                    val name = c.getColumnIndexOrThrow("name")
                    while (c.moveToNext()) add(c.getString(name))
                }
            }
        }
        statements.forEach(db::execSQL)
    }
}

/** Every migration, in order. [IntakeDatabase] registers all of them. */
internal val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

/** Statement lists by starting version, for `MigrationSchemaTest`. Version 3 is computed: see [migrateRepair_3_4]. */
internal val MIGRATION_STATEMENTS: Map<Int, List<String>> = mapOf(
    1 to MIGRATION_1_2_STATEMENTS,
    2 to MIGRATION_2_3_STATEMENTS,
)
