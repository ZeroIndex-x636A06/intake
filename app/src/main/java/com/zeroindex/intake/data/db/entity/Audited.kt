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
