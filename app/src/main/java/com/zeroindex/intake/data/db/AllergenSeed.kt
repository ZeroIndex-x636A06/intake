package com.zeroindex.intake.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.zeroindex.intake.domain.model.AllergenSeverity

/**
 * SPEC §4: seed the alias table at first run with the dairy aliases. This list
 * is the point of the allergen engine - "sodium caseinate" on an ingredient
 * panel is dairy, and nothing on the label says so.
 */
internal object AllergenSeed {

    const val DAIRY = "dairy"

    val DAIRY_ALIASES: List<String> = listOf(
        "milk", "butter", "cream", "cheese", "whey", "casein", "caseinate",
        "sodium caseinate", "lactose", "ghee", "curds", "milkfat", "buttermilk",
        "custard", "half and half", "condensed milk", "evaporated milk",
        "nonfat dry milk", "milk solids", "lactalbumin", "lactoglobulin",
        "rennet casein", "dairy",
    )

    /**
     * Written with raw SQL inside Room's `onCreate` so the table is never
     * observable in an unseeded state - no coroutine, no race with the first
     * read. Ids are deterministic so a re-seed cannot duplicate a row.
     */
    fun seed(db: SupportSQLiteDatabase, now: Long) {
        DAIRY_ALIASES.forEach { alias ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO allergen_alias
                    (id, allergen, aliasTerm, createdAt, updatedAt, deletedAt)
                VALUES (?, ?, ?, ?, ?, NULL)
                """.trimIndent(),
                arrayOf<Any?>("seed-$DAIRY-${alias.replace(' ', '-')}", DAIRY, alias, now, now),
            )
        }

        // A profile row has to exist for the engine to have anything to check
        // against. Dairy is pre-filled because it is why this app exists; it is
        // editable in Settings.
        db.execSQL(
            """
            INSERT OR IGNORE INTO allergen_profile
                (id, avoidedAllergens, severityLevel, notes, createdAt, updatedAt, deletedAt)
            VALUES (?, ?, ?, '', ?, ?, NULL)
            """.trimIndent(),
            arrayOf<Any?>("allergen-profile", DAIRY, AllergenSeverity.AVOID.name, now, now),
        )
    }
}
