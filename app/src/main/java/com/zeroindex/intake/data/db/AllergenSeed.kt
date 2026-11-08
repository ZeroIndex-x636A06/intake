package com.zeroindex.intake.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.zeroindex.intake.domain.allergen.AllergenCatalog
import com.zeroindex.intake.domain.model.AllergenSeverity

/**
 * Seeds the built-in allergens and their keywords (docs/ALLERGENS.md). The dairy list is the point of
 * the engine - "sodium caseinate" on an ingredient panel is dairy, and nothing on the label says so.
 *
 * Rules, because users edit these rows:
 * - ids are deterministic ([keywordId], [definitionId]), so two devices seed the same rows and sync merges them;
 * - every insert is INSERT OR IGNORE: an existing row is never overwritten, so a keyword the user
 *   switched off stays off and a level is never weakened;
 * - timestamps are the fixed SEED_TIMESTAMP, so a fresh install can never out-date an edit made on
 *   another device.
 */
internal object AllergenSeed {

    const val DAIRY = AllergenCatalog.DAIRY

    /** The original dairy aliases (kept for tests that count them). */
    val DAIRY_ALIASES: List<String> = AllergenCatalog.DAIRY_CORE

    fun keywordId(allergen: String, term: String) = "seed-${allergen.replace(' ', '-')}-${term.replace(' ', '-')}"

    fun definitionId(allergen: String) = "seed-allergen-${allergen.replace(' ', '-')}"

    /** Number of built-in keyword rows. */
    val keywordCount: Int = AllergenCatalog.ALL.sumOf { it.keywords.size }

    /** First-run seed: definitions, keywords and the default profile. */
    fun seed(db: SupportSQLiteDatabase, now: Long) {
        topUpRows(db, now)

        // A profile row has to exist for the engine to have anything to check against. Dairy is
        // pre-filled because it is why this app exists; it is editable in My allergens.
        db.execSQL(
            """
            INSERT OR IGNORE INTO allergen_profile
                (id, avoidedAllergens, severityLevel, notes, createdAt, updatedAt, deletedAt)
            VALUES (?, ?, ?, '', ?, ?, NULL)
            """.trimIndent(),
            arrayOf<Any?>("allergen-profile", DAIRY, AllergenSeverity.AVOID.name, now, now),
        )
    }

    /** Runs on every open; does nothing once every built-in row is present. */
    fun topUp(db: SupportSQLiteDatabase, now: Long) {
        val present = db.query("SELECT COUNT(*) FROM allergen_alias WHERE id LIKE 'seed-%'").use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        val defs = db.query("SELECT COUNT(*) FROM allergen_definition WHERE id LIKE 'seed-%'").use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (present >= keywordCount && defs >= AllergenCatalog.ALL.size) return
        topUpRows(db, now)
    }

    private fun topUpRows(db: SupportSQLiteDatabase, now: Long) {
        db.beginTransaction()
        try {
            AllergenCatalog.ALL.forEachIndexed { index, a ->
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO allergen_definition
                        (id, name, displayName, description, isCustom, sortOrder, createdAt, updatedAt, deletedAt)
                    VALUES (?, ?, ?, ?, 0, ?, ?, ?, NULL)
                    """.trimIndent(),
                    arrayOf<Any?>(definitionId(a.key), a.key, a.displayName, a.summary, index, now, now),
                )
                a.keywords.forEach { k ->
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO allergen_alias
                            (id, allergen, aliasTerm, level, isCustom, isEnabled, createdAt, updatedAt, deletedAt)
                        VALUES (?, ?, ?, ?, 0, 1, ?, ?, NULL)
                        """.trimIndent(),
                        arrayOf<Any?>(keywordId(a.key, k.term), a.key, k.term, k.level.name, now, now),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
