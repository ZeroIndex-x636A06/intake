package com.zeroindex.intake.domain.sync

import com.zeroindex.intake.domain.backup.MergePlan
import com.zeroindex.intake.domain.backup.RowVersion

/** A local row as sync sees it. [json] is kept so a conflict can store the losing side verbatim. */
data class LocalRow(val version: RowVersion, val json: String)

/** A row arriving from the server. */
data class IncomingRow(val version: RowVersion, val json: String)

data class Conflict(
    val rowId: String,
    val keptJson: String,
    val otherJson: String,
    /** Where the losing version was made. */
    val otherSource: String,
)

data class SyncPlan(
    /** Incoming row index -> the id to write it under (differs when matched by natural key). */
    val writes: List<Pair<Int, String>>,
    val conflicts: List<Conflict>,
)

/**
 * Amendment §3: apply what the server sent, the same way every device does.
 *
 * Which version a row ends up as is decided by [MergePlan.incomingWins]
 * alone - newest `updatedAt`, ties by content hash - so all devices and the
 * server converge. What this adds is noticing a *concurrent* edit: a row
 * this device changed since its last push ([pushedUpTo]) that also arrives
 * changed from elsewhere. Whichever side loses is returned as a [Conflict]
 * so the user can see it and bring it back; nothing is silently overwritten.
 */
object SyncMerge {

    const val THIS_DEVICE = "this device"
    const val OTHER_DEVICE = "another device"

    fun plan(
        local: Collection<LocalRow>,
        incoming: List<IncomingRow>,
        pushedUpTo: Long,
        trackConflicts: Boolean = true,
    ): SyncPlan {
        val byId = local.associateBy { it.version.id }
        val byKey = local.filter { it.version.naturalKey != null }.associateBy { it.version.naturalKey!! }
        val writes = ArrayList<Pair<Int, String>>()
        val conflicts = ArrayList<Conflict>()

        // Several versions of one row can arrive in one pull only if the
        // server sent a stale page; keep the winner among them first.
        val latest = incoming.withIndex()
            .groupBy { it.value.version.id }
            .map { (_, versions) -> versions.reduce { a, b -> if (MergePlan.incomingWins(b.value.version, a.value.version)) b else a } }

        for ((index, row) in latest) {
            val existing = byId[row.version.id] ?: row.version.naturalKey?.let(byKey::get)
            if (existing == null) {
                writes += index to row.version.id
                continue
            }
            if (existing.version.hash == row.version.hash) continue
            val localEdited = existing.version.updatedAt > pushedUpTo
            if (MergePlan.incomingWins(row.version, existing.version)) {
                writes += index to existing.version.id
                if (trackConflicts && localEdited) conflicts += Conflict(existing.version.id, row.json, existing.json, THIS_DEVICE)
            } else if (trackConflicts && localEdited) {
                conflicts += Conflict(existing.version.id, existing.json, row.json, OTHER_DEVICE)
            }
        }
        return SyncPlan(writes.sortedBy { it.first }, conflicts)
    }
}
