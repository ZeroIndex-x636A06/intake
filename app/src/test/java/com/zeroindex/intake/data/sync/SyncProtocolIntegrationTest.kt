package com.zeroindex.intake.data.sync

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.backup.RowVersion
import com.zeroindex.intake.domain.sync.IncomingRow
import com.zeroindex.intake.domain.sync.LocalRow
import com.zeroindex.intake.domain.sync.SyncMerge
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Two simulated devices sync through the real server/intake_sync.py, using
 * the app's SyncApi and SyncMerge. Proves the wire format, pairing, paging,
 * convergence after concurrent edits and conflict capture end to end.
 * Off unless INTAKE_SYNC_IT=1 (needs python3).
 */
class SyncProtocolIntegrationTest {

    private lateinit var server: Process
    private lateinit var url: String
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }
    private val http = OkHttpClient()

    @Before
    fun start() {
        assumeTrue("set INTAKE_SYNC_IT=1 to run", System.getenv("INTAKE_SYNC_IT") == "1")
        val script = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "server/intake_sync.py") }.first { it.exists() }
        val port = ServerSocket(0).use { it.localPort }
        val db = File.createTempFile("intake-sync", ".db").also { it.delete() }
        server = ProcessBuilder("python3", script.path, "serve")
            .apply {
                environment()["INTAKE_SYNC_DB"] = db.path
                environment()["INTAKE_SYNC_HOST"] = "127.0.0.1"
                environment()["INTAKE_SYNC_PORT"] = port.toString()
            }
            .redirectErrorStream(true)
            .start()
        url = "http://127.0.0.1:$port"
        // First line on a fresh database is the pairing code.
        val reader = server.inputStream.bufferedReader()
        firstCode = Regex("[2-9A-Z]{4}-[2-9A-Z]{4}").find(reader.readLine())!!.value
        reader.readLine() // "Run ... pair"
        reader.readLine() // "listening on"
        Thread { reader.forEachLine { } }.apply { isDaemon = true }.start()
    }

    private lateinit var firstCode: String

    @After
    fun stop() {
        if (::server.isInitialized) server.destroy()
    }

    /** A device reduced to a map of rows plus its sync cursor. */
    private inner class Device(name: String, code: String) {
        val api = SyncApi(http, json, url)
        val token: String = runBlocking { api.pair(code, name).token }
        val rows = LinkedHashMap<String, JsonObject>()
        var pulled = 0L
        var pushedUpTo = 0L
        val conflicts = ArrayList<String>()

        fun edit(id: String, value: String, at: Long) {
            rows[id] = JsonObject(mapOf("id" to JsonPrimitive(id), "v" to JsonPrimitive(value), "updatedAt" to JsonPrimitive(at)))
        }

        private fun hash(o: JsonObject) = MessageDigest.getInstance("SHA-256").digest(o.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        private fun version(o: JsonObject) = RowVersion(o["id"]!!.jsonPrimitive.content, o["updatedAt"]!!.jsonPrimitive.long, hash = hash(o))

        fun sync() = runBlocking {
            val incoming = ArrayList<WireRow>()
            do {
                val page = api.pull(token, pulled, limit = 2) // tiny pages to exercise paging
                incoming += page.rows
                pulled = page.next
            } while (page.more)
            val plan = SyncMerge.plan(
                rows.values.map { LocalRow(version(it), it.toString()) },
                incoming.map { IncomingRow(RowVersion(it.id, it.updatedAt, hash = it.hash), it.payload.toString()) },
                pushedUpTo,
            )
            plan.writes.forEach { (i, id) -> rows[id] = incoming[i].payload }
            plan.conflicts.forEach { conflicts += it.otherJson }
            val out = rows.values.filter { version(it).updatedAt > pushedUpTo }.map {
                WireRow("food_log", version(it).id, version(it).updatedAt, null, hash(it), it)
            }
            if (out.isNotEmpty()) api.push(token, out)
            pushedUpTo = rows.values.maxOfOrNull { version(it).updatedAt } ?: pushedUpTo
        }
    }

    @Test
    fun twoDevicesConvergeAndKeepTheLosingEdit() {
        val phone = Device("phone", firstCode)
        val tabletCode = runBlocking { phone.api.newPairingCode(phone.token).code }
        val tablet = Device("tablet", tabletCode)

        // Offline on both: different rows, plus the same row edited on each.
        phone.edit("breakfast", "oats", 100)
        phone.edit("lunch", "rice", 110)
        phone.edit("snack", "apple", 120)
        tablet.edit("dinner", "salmon", 105)

        phone.sync()
        tablet.sync()
        phone.sync()
        assertThat(phone.rows.keys).containsExactly("breakfast", "lunch", "snack", "dinner")
        assertThat(tablet.rows).isEqualTo(phone.rows)

        // Concurrent edit of one row; the later one wins everywhere.
        phone.edit("lunch", "rice and beans", 200)
        tablet.edit("lunch", "rice, no beans", 210)
        phone.sync()
        tablet.sync()
        phone.sync()
        assertThat(phone.rows).isEqualTo(tablet.rows)
        assertThat(phone.rows["lunch"]!!["v"]!!.jsonPrimitive.content).isEqualTo("rice, no beans")
        // The tablet synced second, holding an unpushed edit when the phone's
        // arrived: it records the clash and keeps the losing version to restore.
        assertThat(tablet.conflicts.single()).contains("rice and beans")
        assertThat(phone.conflicts).isEmpty()

        val devices = runBlocking { phone.api.devices(phone.token) }
        assertThat(devices.map { it.name }).containsExactly("phone", "tablet")
    }
}
