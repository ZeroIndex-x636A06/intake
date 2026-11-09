package com.zeroindex.intake.data.backup

import androidx.room.withTransaction
import com.zeroindex.intake.data.db.IntakeDatabase
import com.zeroindex.intake.data.db.dao.ExerciseDao
import com.zeroindex.intake.data.db.dao.ExportDao
import com.zeroindex.intake.data.db.entity.Audited
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.backup.BackupCrypto
import com.zeroindex.intake.domain.backup.MergePlan
import com.zeroindex.intake.domain.backup.RowVersion
import com.zeroindex.intake.domain.backup.TablePlan
import com.zeroindex.intake.domain.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

open class ImportException(message: String) : Exception(message)

/** The passphrase did not open the file; the UI asks again rather than giving up. */
class WrongPassphraseException(message: String) : ImportException(message)

/** What an import would do, shown before anything is written (SPEC §13). */
class ImportPreview internal constructor(
    val schemaVersion: Int,
    val exportedAt: Long?,
    val plans: List<TablePlan>,
    val unknownTables: List<String>,
    internal val rows: Map<String, List<Audited>>,
) {
    val totalChanges: Int get() = plans.sumOf { it.changes }
}

/**
 * SPEC §13 export and import, and the payload for amendment §2's headless
 * backups and §3's sync.
 *
 * The export is every row of every table, soft-deleted ones included, so it
 * round-trips losslessly. Import decodes every table with its own typed
 * serializer - a field of the wrong type or a missing required field fails
 * the whole import with the table named - then merges with [MergePlan] and
 * writes in one transaction, parents before children.
 */
@Singleton
class BackupService @Inject constructor(
    private val db: IntakeDatabase,
    private val exportDao: ExportDao,
    private val exerciseDao: ExerciseDao,
    private val settings: SettingsRepository,
    private val json: Json,
    private val clock: Clock,
) {
    companion object {
        const val FORMAT = "intake-export"
        const val FORMAT_VERSION = 1
    }

    private val tables by lazy { backupTables(exportDao) }

    @Suppress("UNCHECKED_CAST") // each table's rows are encoded with that table's own serializer
    private fun <T : Audited> BackupTable<out Audited>.typed() = this as BackupTable<T>

    suspend fun exportJson(): ByteArray {
        val tableJson = tables.associate { table ->
            val t = table.typed<Audited>()
            t.name to JsonArray(t.read().map { json.encodeToJsonElement(t.serializer, it) })
        }
        val root = JsonObject(
            mapOf(
                "format" to JsonPrimitive(FORMAT),
                "formatVersion" to JsonPrimitive(FORMAT_VERSION),
                "schemaVersion" to JsonPrimitive(IntakeDatabase.VERSION),
                "exportedAt" to JsonPrimitive(clock.nowMillis()),
                "tables" to JsonObject(tableJson),
            ),
        )
        return json.encodeToString(JsonObject.serializer(), root).toByteArray()
    }

    /** Export, optionally sealed with a passphrase; records the export for the 30-day prompt. */
    suspend fun exportBytes(passphrase: CharArray?): ByteArray {
        val plain = exportJson()
        val out = if (passphrase != null && passphrase.isNotEmpty()) BackupCrypto.encrypt(plain, passphrase) else plain
        settings.setLastExportAt(clock.nowMillis())
        return out
    }

    fun needsPassphrase(bytes: ByteArray): Boolean = BackupCrypto.isEncrypted(bytes)

    suspend fun preview(bytes: ByteArray, passphrase: CharArray? = null): ImportPreview {
        val plain = if (BackupCrypto.isEncrypted(bytes)) {
            if (passphrase == null) throw ImportException("This backup is encrypted. Enter its passphrase.")
            try {
                BackupCrypto.decrypt(bytes, passphrase)
            } catch (e: BackupCrypto.WrongPassphrase) {
                throw WrongPassphraseException(e.message ?: "Wrong passphrase.")
            }
        } else {
            bytes
        }
        val root = runCatching { json.parseToJsonElement(plain.decodeToString()).jsonObject }
            .getOrElse { throw ImportException("That file is not an Intake backup (it is not JSON).") }
        if (root["format"]?.jsonPrimitive?.contentOrNull != FORMAT) throw ImportException("That file is not an Intake backup.")
        val schema = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: throw ImportException("The backup has no schema version.")
        if (schema > IntakeDatabase.VERSION) {
            throw ImportException("This backup is from a newer version of Intake (schema $schema). Update the app first.")
        }
        val incoming = root["tables"]?.jsonObject ?: throw ImportException("The backup has no tables.")

        val rows = LinkedHashMap<String, List<Audited>>()
        val plans = tables.mapNotNull { table ->
            val t = table.typed<Audited>()
            val array = incoming[t.name]?.jsonArray ?: return@mapNotNull null
            val decoded = try {
                array.map { json.decodeFromJsonElement(t.serializer, LegacyRows.upgrade(t.name, it)) }
            } catch (e: SerializationException) {
                throw ImportException("The ${t.name} table in the backup does not match this app: ${e.message?.take(120)}")
            } catch (e: IllegalArgumentException) {
                throw ImportException("The ${t.name} table in the backup does not match this app: ${e.message?.take(120)}")
            }
            rows[t.name] = decoded
            MergePlan.plan(
                table = t.name,
                local = t.read().map { RowVersion(it.id, it.updatedAt, t.naturalKey?.invoke(it)) },
                incoming = decoded.map { RowVersion(it.id, it.updatedAt, t.naturalKey?.invoke(it)) },
            )
        }
        return ImportPreview(
            schemaVersion = schema,
            exportedAt = root["exportedAt"]?.jsonPrimitive?.longOrNull,
            plans = plans,
            unknownTables = incoming.keys.filter { name -> tables.none { it.name == name } },
            rows = rows,
        )
    }

    /** Writes exactly what [preview] showed, in one transaction. */
    suspend fun apply(preview: ImportPreview) {
        db.withTransaction {
            tables.forEach { table ->
                val t = table.typed<Audited>()
                val plan = preview.plans.firstOrNull { it.table == t.name } ?: return@forEach
                if (plan.writes.isEmpty()) return@forEach
                val toWrite = preview.rows[t.name].orEmpty().mapNotNull { row ->
                    val target = plan.writes[row.id] ?: return@mapNotNull null
                    if (target != row.id) t.withId?.invoke(row, target) ?: row else row
                }
                t.write(toWrite)
            }
        }
        // Plan weeks from a schema-2 backup have no plan yet.
        com.zeroindex.intake.data.plan.LegacyPlanRepair.run(db, clock.nowMillis(), clock.today().toEpochDay())
    }

    // ----- CSV, per domain (SPEC §13) --------------------------------------

    suspend fun foodLogCsv(): ByteArray {
        val zone = clock.zone()
        val rows = exportDao.foodLog().filter { it.deletedAt == null }.sortedBy { it.loggedAt }
        return csv(
            listOf("date", "time", "meal", "food", "grams", "kcal", "protein_g", "carbs_g", "fat_g", "fiber_g", "sugar_g", "sodium_mg"),
            rows.map {
                val t = Instant.ofEpochMilli(it.loggedAt).atZone(zone)
                listOf(
                    t.toLocalDate().toString(), t.toLocalTime().withNano(0).toString(), it.mealSlot.name.lowercase(),
                    it.foodNameAtLog, it.quantityGrams?.let(::num), num(it.kcal), num(it.protein), num(it.carbs),
                    num(it.fat), num(it.fiber), num(it.sugar), num(it.sodium),
                )
            },
        )
    }

    suspend fun workoutCsv(): ByteArray {
        val zone = clock.zone()
        val unit = settings.settings.first().weightUnit
        val names = exerciseDao.all().associate { it.id to it.name }
        val sessions = exportDao.workoutSession().filter { it.deletedAt == null }.associateBy { it.id }
        val sets = exportDao.setLog().filter { it.deletedAt == null && it.sessionId in sessions }
            .sortedWith(compareBy({ sessions.getValue(it.sessionId).startedAt }, { it.completedAt }))
        return csv(
            listOf("date", "session_id", "exercise", "set", "weight_${unit.label}", "reps", "rpe", "warmup", "to_failure"),
            sets.map {
                listOf(
                    Instant.ofEpochMilli(sessions.getValue(it.sessionId).startedAt).atZone(zone).toLocalDate().toString(),
                    it.sessionId, names[it.exerciseId] ?: it.exerciseId, (it.setIndex + 1).toString(),
                    num(unit.fromKg(it.weightKg)), it.reps.toString(), it.rpe?.let(::num), it.isWarmup.toString(), it.isFailure.toString(),
                )
            },
        )
    }

    suspend fun bodyMetricCsv(): ByteArray {
        val zone = clock.zone()
        val s = settings.settings.first()
        val w = s.weightUnit
        val l = s.lengthUnit
        val rows = exportDao.bodyMetric().filter { it.deletedAt == null }.sortedBy { it.measuredAt }
        return csv(
            listOf(
                "date", "weight_${w.label}", "body_fat_pct", "lean_mass_${w.label}", "skeletal_muscle_${w.label}",
                "waist_${l.label}", "chest_${l.label}", "arm_${l.label}", "thigh_${l.label}", "hip_${l.label}", "source",
            ),
            rows.map {
                listOf(
                    Instant.ofEpochMilli(it.measuredAt).atZone(zone).toLocalDate().toString(), num(w.fromKg(it.weightKg)),
                    it.bodyFatPercent?.let(::num), it.leanBodyMassKg?.let { v -> num(w.fromKg(v)) },
                    it.skeletalMuscleKg?.let { v -> num(w.fromKg(v)) }, it.waistCm?.let { v -> num(l.fromCm(v)) },
                    it.chestCm?.let { v -> num(l.fromCm(v)) }, it.armCm?.let { v -> num(l.fromCm(v)) },
                    it.thighCm?.let { v -> num(l.fromCm(v)) }, it.hipCm?.let { v -> num(l.fromCm(v)) }, it.source.lowercase(),
                )
            },
        )
    }

    fun fileStamp(): String = DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(clock.nowMillis()).atZone(clock.zone()))

    private fun num(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, v)

    private fun csv(header: List<String>, rows: List<List<String?>>): ByteArray =
        (listOf(header) + rows).joinToString("\r\n", postfix = "\r\n") { row ->
            row.joinToString(",") { cell -> CsvCell.escape(cell.orEmpty()) }
        }.toByteArray()
}

/** RFC 4180 quoting: only when needed, doubling inner quotes. */
object CsvCell {
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value
}
