package com.zeroindex.intake.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/** One page of records and the token for the next page, or null on the last page. */
data class RecordPage<T>(val records: List<T>, val pageToken: String?)

/**
 * The seam between Intake and Health Connect. [HealthConnectSource] is the real
 * thing; tests use a fake so first-sync range, paging and permission subsets can
 * be checked without a phone. Methods throw on failure: the caller turns that
 * into a per-type message instead of silently treating it as "no data".
 */
interface HealthDataSource {
    /** Package name of this app, so records we wrote ourselves are not read back. */
    val ownPackage: String

    fun availability(): HealthAvailability

    suspend fun grantedPermissions(): Set<String>

    suspend fun <T : Record> readPage(type: KClass<T>, from: Instant, to: Instant, pageToken: String?): RecordPage<T>

    /** De-duplicated daily totals (Health Connect resolves overlapping sources). Null when there is no data. */
    suspend fun totalSteps(from: Instant, to: Instant): Long?
    suspend fun totalCaloriesBurned(from: Instant, to: Instant): Double?
    suspend fun averageHeartRate(from: Instant, to: Instant): Double?
    /** De-duplicated all-day active calories (above resting burn). Null when there is no data. */
    suspend fun totalActiveCalories(from: Instant, to: Instant): Double?

    /**
     * Whether this phone's Health Connect can grant "read older history". Without it an app only sees data from the
     * 30 days before it was first allowed, which is why weigh-ins from before connecting never arrive.
     */
    suspend fun historyPermissionSupported(): Boolean

    suspend fun insert(records: List<Record>)
    suspend fun delete(type: KClass<out Record>, clientRecordIds: List<String>)
}

private const val MAX_PAGES = 500

/** Follows every page. The page cap only guards against a provider that never ends. */
suspend fun <T : Record> HealthDataSource.readAll(type: KClass<T>, from: Instant, to: Instant): List<T> {
    val out = mutableListOf<T>()
    var token: String? = null
    var pages = 0
    do {
        val page = readPage(type, from, to, token)
        out += page.records
        token = page.pageToken
        pages++
    } while (token != null && pages < MAX_PAGES)
    return out
}

@Singleton
class HealthConnectSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthDataSource {
    override val ownPackage: String get() = context.packageName

    override fun availability(): HealthAvailability = when (runCatching { HealthConnectClient.getSdkStatus(context) }.getOrNull()) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        HealthConnectClient.SDK_UNAVAILABLE -> HealthAvailability.NOT_INSTALLED
        else -> HealthAvailability.UNSUPPORTED
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    override suspend fun grantedPermissions(): Set<String> = client().permissionController.getGrantedPermissions()

    override suspend fun <T : Record> readPage(type: KClass<T>, from: Instant, to: Instant, pageToken: String?): RecordPage<T> {
        val response = client().readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageToken = pageToken))
        return RecordPage(response.records, response.pageToken)
    }

    override suspend fun totalSteps(from: Instant, to: Instant): Long? =
        client().aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(from, to)))[StepsRecord.COUNT_TOTAL]

    override suspend fun totalCaloriesBurned(from: Instant, to: Instant): Double? =
        client().aggregate(AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), TimeRangeFilter.between(from, to)))[
            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
        ]?.inKilocalories

    override suspend fun averageHeartRate(from: Instant, to: Instant): Double? =
        client().aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), TimeRangeFilter.between(from, to)))[HeartRateRecord.BPM_AVG]
            ?.toDouble()

    override suspend fun totalActiveCalories(from: Instant, to: Instant): Double? =
        client().aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(from, to)))[
            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
        ]?.inKilocalories

    override suspend fun historyPermissionSupported(): Boolean = runCatching {
        client().features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    override suspend fun insert(records: List<Record>) {
        client().insertRecords(records)
    }

    override suspend fun delete(type: KClass<out Record>, clientRecordIds: List<String>) {
        client().deleteRecords(type, emptyList(), clientRecordIds)
    }
}
