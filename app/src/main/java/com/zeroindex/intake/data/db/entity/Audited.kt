package com.zeroindex.intake.data.db.entity

import java.util.UUID

/**
 * Every table in this database carries the same four columns (SPEC §4):
 * a UUID string primary key and created / updated / deleted timestamps, all
 * UTC epoch millis (SPEC §3.4).
 *
 * SPEC §3.8: nothing is ever hard deleted. [deletedAt] going non-null is what
 * "delete" means, and every read query filters on `deletedAt IS NULL`.
 */
interface Audited {
    val id: String
    val createdAt: Long
    val updatedAt: Long
    val deletedAt: Long?
}

/** Fresh UUID for a new row. */
fun newId(): String = UUID.randomUUID().toString()

/**
 * The id of a row the app seeds itself (starter exercises, default presets).
 * Derived from what the row is, not random, so two devices that each seed on
 * first launch create the *same* rows and sync merges them instead of
 * showing every starter exercise twice (amendment §3).
 */
/**
 * createdAt / updatedAt for seeded rows: a fixed instant (2026-01-01 UTC)
 * rather than "now". A device seeding today must never out-date an edit the
 * user made on another device yesterday - with "now", a new tablet's default
 * allergen profile would win the sync over the one the user set up.
 */
const val SEED_TIMESTAMP: Long = 1_767_225_600_000L

fun seedId(kind: String, key: String): String =
    UUID.nameUUIDFromBytes("intake-seed:$kind:${key.trim().lowercase()}".toByteArray()).toString()
