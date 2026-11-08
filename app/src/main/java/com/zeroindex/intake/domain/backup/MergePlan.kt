package com.zeroindex.intake.domain.backup

/**
 * A row reduced to what merging needs. [hash] is a digest of the row's
 * content; when two versions carry the same [updatedAt] it decides the
 * winner, so every device picks the same one.
 */
data class RowVersion(val id: String, val updatedAt: Long, val naturalKey: String? = null, val hash: String? = null)

data class TablePlan(
    val table: String,
    /** Ids from the backup to write, mapped to the id they should be written under. */
    val writes: Map<String, String>,
    val added: Int,
    val updated: Int,
    val keptLocal: Int,
    val unchanged: Int,
) {
    val changes: Int get() = added + updated
}

/**
 * How an import merges with what is already on the phone (SPEC §13: show a
 * diff before writing). Last writer wins, by `updatedAt`, row by row:
 *
 * - a row the phone does not have is added;
 * - a row the backup has a newer version of is updated;
 * - a row the phone has a newer version of is kept as it is - restoring an
 *   old backup never undoes work done since;
 * - an identical version is left alone.
 *
 * Deletions travel as rows with `deletedAt` set, so they merge by the same
 * rule and a restore never resurrects something deleted later.
 *
 * Sync (amendment §3) applies the same rule, so a backup and a sync can
 * never disagree about which version of a row wins. Equal timestamps with
 * different content are broken by the larger content [RowVersion.hash] -
 * arbitrary, but the same on every device and on the server, which is what
 * convergence needs.
 */
object MergePlan {

    fun plan(table: String, local: List<RowVersion>, incoming: List<RowVersion>): TablePlan {
        val byId = local.associateBy { it.id }
        val byKey = local.filter { it.naturalKey != null }.associateBy { it.naturalKey!! }
        val writes = LinkedHashMap<String, String>()
        var added = 0
        var updated = 0
        var kept = 0
        var same = 0

        incoming.forEach { row ->
            val existing = byId[row.id] ?: row.naturalKey?.let(byKey::get)
            when {
                existing == null -> { writes[row.id] = row.id; added++ }
                incomingWins(row, existing) -> { writes[row.id] = existing.id; updated++ }
                row.updatedAt < existing.updatedAt || row.hash != existing.hash -> kept++
                else -> same++
            }
        }
        return TablePlan(table, writes, added, updated, kept, same)
    }

    /** The one ordering every device and the server agree on. */
    fun incomingWins(incoming: RowVersion, existing: RowVersion): Boolean = when {
        incoming.updatedAt != existing.updatedAt -> incoming.updatedAt > existing.updatedAt
        incoming.hash == null || existing.hash == null -> false
        else -> incoming.hash > existing.hash
    }
}
