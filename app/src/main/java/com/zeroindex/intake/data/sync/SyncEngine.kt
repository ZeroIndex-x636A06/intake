package com.zeroindex.intake.data.sync

import androidx.room.withTransaction
import com.zeroindex.intake.data.backup.BackupTable
import com.zeroindex.intake.data.backup.LegacyRows
import com.zeroindex.intake.data.backup.backupTables
import com.zeroindex.intake.data.backup.remote.BackupCredentialStore
import com.zeroindex.intake.data.db.IntakeDatabase
import com.zeroindex.intake.data.db.dao.ExportDao
import com.zeroindex.intake.data.db.dao.SyncConflictDao
import com.zeroindex.intake.data.db.entity.Audited
import com.zeroindex.intake.data.db.entity.SyncConflictEntity
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.backup.RowVersion
import com.zeroindex.intake.domain.backup.WebDavUrlPolicy
import com.zeroindex.intake.domain.sync.IncomingRow
import com.zeroindex.intake.domain.sync.LocalRow
import com.zeroindex.intake.domain.sync.SyncMerge
import com.zeroindex.intake.domain.time.HybridClock
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient

data class SyncReport(val pulled: Int, val applied: Int, val pushed: Int, val conflicts: Int)

/**
 * Amendment §3's sync client. One run: pull everything new from the server
 * and apply it with [SyncMerge] in a single transaction, then push every
 * local row changed since the last push. Offline-first throughout: a run
 * that cannot reach the server changes nothing and is simply tried again.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val db: IntakeDatabase,
    exportDao: ExportDao,
    private val settings: SettingsRepository,
    private val credentials: BackupCredentialStore,
    private val conflictDao: SyncConflictDao,
    private val clock: HybridClock,
    http: OkHttpClient,
) {
    private companion object {
        /** Device-specific records that mean nothing on another device. */
        val LOCAL_ONLY = setOf("backup_run")
        /** Derived, recomputed data: last writer wins without asking. */
        val NO_CONFLICTS = setOf("health_daily_summary", "ai_insight", "model_benchmark", "tdee_estimate")
        const val PUSH_BATCH = 400
    }

    /** Compact and complete, so the same row always serialises - and hashes - the same way. */
    private val wire = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

    private val http = http.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    private val tables: List<BackupTable<out Audited>> by lazy { backupTables(exportDao).filter { it.name !in LOCAL_ONLY } }
    private val mutex = Mutex()

    @Suppress("UNCHECKED_CAST")
    private fun BackupTable<out Audited>.typed() = this as BackupTable<Audited>

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun encode(table: BackupTable<Audited>, row: Audited): String = wire.encodeToString(table.serializer, row)

    /** Plain http only to an obviously local address, as for WebDAV backups. */
    fun problemWith(serverUrl: String): String? = when (val v = WebDavUrlPolicy.check(serverUrl)) {
        is WebDavUrlPolicy.Verdict.Rejected -> v.reason
        else -> null
    }

    private fun api(serverUrl: String) = SyncApi(http, wire, serverUrl)

    suspend fun pair(serverUrl: String, code: String, deviceName: String) {
        problemWith(serverUrl)?.let { throw SyncException(it) }
        val url = serverUrl.trim().trimEnd('/')
        val paired = api(url).pair(code.trim(), deviceName.trim().ifEmpty { "Android device" })
        credentials.setSyncToken(paired.token)
        settings.setSyncPaired(url, paired.deviceId, deviceName)
    }

    private suspend fun session(): Pair<SyncApi, String> {
        val s = settings.settings.first().sync
        val url = s.serverUrl ?: throw SyncException("This device is not paired.")
        val token = credentials.syncToken() ?: throw SyncException("This device's sync token is missing. Pair it again.")
        return api(url) to token
    }

    suspend fun newPairingCode(): PairingCode = session().let { (api, token) -> api.newPairingCode(token) }
    suspend fun devices(): List<SyncDevice> = session().let { (api, token) -> api.devices(token) }
    suspend fun revoke(deviceId: String) = session().let { (api, token) -> api.revoke(token, deviceId) }

    /** Stops syncing here. The data stays; the server keeps its copy until this device is removed there. */
    suspend fun unpair() {
        credentials.setSyncToken(null)
        settings.clearSync()
    }

    suspend fun sync(): SyncReport = mutex.withLock {
        val state = settings.settings.first().sync
        if (!state.paired) throw SyncException("This device is not paired.")
        val (api, token) = session()
        try {
            val since = if (state.schemaVersion == IntakeDatabase.VERSION) state.pulledSeq else 0L

            // Pull every page before applying, so a child row never lands before its parent.
            val incoming = ArrayList<WireRow>()
            var cursor = since
            do {
                val page = api.pull(token, cursor)
                incoming += page.rows
                cursor = page.next
            } while (page.more)

            val (applied, conflicts) = apply(incoming, state.pushedUpTo)
            settings.setSyncCursor(pulledSeq = cursor, schemaVersion = IntakeDatabase.VERSION)

            val pushed = push(api, token, state.pushedUpTo)
            settings.setSyncResult(clock.nowMillis(), null)
            SyncReport(pulled = incoming.size, applied = applied, pushed = pushed, conflicts = conflicts)
        } catch (e: SyncException) {
            settings.setSyncResult(clock.nowMillis(), if (e.unauthorized) "This device was removed from the sync server. Pair it again." else e.message)
            throw e
        }
    }

    private suspend fun apply(rows: List<WireRow>, pushedUpTo: Long): Pair<Int, Int> {
        if (rows.isEmpty()) return 0 to 0
        val byTable = rows.groupBy { it.table }
        val now = clock.nowMillis()
        val toWrite = ArrayList<Pair<BackupTable<Audited>, List<Audited>>>()
        val conflictRows = ArrayList<SyncConflictEntity>()

        for (table in tables.map { it.typed() }) {
            val wireRows = byTable[table.name] ?: continue
            val decoded = wireRows.map { wr ->
                try {
                    wire.decodeFromJsonElement(table.serializer, LegacyRows.upgrade(table.name, wr.payload))
                } catch (e: Exception) {
                    throw SyncException("A ${table.name.replace('_', ' ')} row from the server does not match this app version. Update Intake on every device.", cause = e)
                }
            }
            val locals = table.read()
            val plan = SyncMerge.plan(
                local = locals.map { row ->
                    val json = encode(table, row)
                    LocalRow(RowVersion(row.id, row.updatedAt, table.naturalKey?.invoke(row), sha256(json)), json)
                },
                incoming = decoded.mapIndexed { i, row ->
                    IncomingRow(RowVersion(row.id, row.updatedAt, table.naturalKey?.invoke(row), wireRows[i].hash), encode(table, row))
                },
                pushedUpTo = pushedUpTo,
                trackConflicts = table.name !in NO_CONFLICTS,
            )
            val writes = plan.writes.map { (i, targetId) ->
                val row = decoded[i]
                if (targetId != row.id) table.withId?.invoke(row, targetId) ?: row else row
            }
            if (writes.isNotEmpty()) toWrite += table to writes
            plan.conflicts.forEach { c ->
                conflictRows += SyncConflictEntity(
                    tableName = table.name,
                    rowId = c.rowId,
                    keptJson = c.keptJson,
                    otherJson = c.otherJson,
                    otherSource = c.otherSource,
                    detectedAt = now,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        }

        db.withTransaction {
            // Checked at commit, not per statement: rows arrive in server order, not dependency order.
            db.openHelper.writableDatabase.execSQL("PRAGMA defer_foreign_keys = ON")
            toWrite.forEach { (table, list) -> table.write(list) }
            conflictRows.forEach { conflictDao.upsert(it) }
        }
        rows.maxOfOrNull { it.updatedAt }?.let(clock::observe)
        return toWrite.sumOf { it.second.size } to conflictRows.size
    }

    private suspend fun push(api: SyncApi, token: String, pushedUpTo: Long): Int {
        // Everything changed after the last push and up to now. Anything written
        // while this runs gets a later timestamp and goes next time.
        val upTo = clock.nowMillis()
        val outgoing = tables.map { it.typed() }.flatMap { table ->
            table.read().filter { it.updatedAt in (pushedUpTo + 1)..upTo }.map { row ->
                val json = encode(table, row)
                WireRow(
                    table = table.name,
                    id = row.id,
                    updatedAt = row.updatedAt,
                    deletedAt = row.deletedAt,
                    hash = sha256(json),
                    payload = wire.parseToJsonElement(json).jsonObject,
                )
            }
        }
        outgoing.chunked(PUSH_BATCH).forEach { api.push(token, it) }
        settings.setSyncCursor(pushedUpTo = upTo)
        return outgoing.size
    }

    /**
     * Writes the losing side of a conflict back, stamped now so it wins and
     * syncs to the other devices. Only the version the user picked is written.
     */
    suspend fun restore(conflictId: String) {
        val conflict = conflictDao.byId(conflictId) ?: return
        val table = tables.firstOrNull { it.name == conflict.tableName }?.typed() ?: return
        val now = clock.nowMillis()
        val other = wire.parseToJsonElement(conflict.otherJson).jsonObject
        val restamped = JsonObject(other + ("updatedAt" to JsonPrimitive(now)))
        table.write(listOf(wire.decodeFromJsonElement(table.serializer, restamped)))
        conflictDao.upsert(conflict.copy(resolvedAt = now, updatedAt = now))
    }

    suspend fun dismiss(conflictId: String) {
        val conflict = conflictDao.byId(conflictId) ?: return
        val now = clock.nowMillis()
        conflictDao.upsert(conflict.copy(resolvedAt = now, updatedAt = now))
    }
}
