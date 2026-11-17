package com.zeroindex.intake.wearprotocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteProtocolTest {
    private val state = RemoteState(
        dateKey = "2026-11-12",
        kcal = 1240, kcalTarget = 2300, proteinG = 88, proteinTargetG = 160,
        waterMl = 1200, waterTargetMl = 2800, volumeUnit = "fl oz",
        waterSizes = listOf(WaterSize("Glass", 237, "p1"), WaterSize("Bottle", 500)),
        planned = PlannedMeal("Chicken bowl", 620, 48, allergen = "MAY_CONTAIN"),
        recents = listOf(WatchFood("f1", "Greek yogurt", 170.0, "1 cup", 100)),
        favourites = emptyList(),
        templates = listOf(WatchTemplate("t1", "Usual breakfast", 450)),
        todayWorkout = TodayWorkout("d1", "Day A · Lower"),
        updatedAt = 5,
    )

    @Test
    fun `state round trips and tolerates fields from a newer phone`() {
        assertThat(RemoteProtocol.decodeState(RemoteProtocol.encode(state))).isEqualTo(state)
        val extra = RemoteProtocol.encode(state).dropLast(1) + ""","somethingNew":1}"""
        assertThat(RemoteProtocol.decodeState(extra)).isEqualTo(state)
    }

    @Test
    fun `a request round trips`() {
        val r = RemoteRequest("r1", RequestKind.LOG_FOOD, at = 10, foodId = "f1", grams = 170.0, slot = WatchSlot.LUNCH, label = "Greek yogurt")
        assertThat(RemoteProtocol.decodeRequest(RemoteProtocol.encode(r))).isEqualTo(r)
    }

    @Test
    fun `a response round trips and a warning survives`() {
        val r = RemoteResponse("r1", ResponseStatus.BLOCKED, "Contains peanut", listOf("peanut"))
        val back = RemoteProtocol.decodeResponse(RemoteProtocol.encode(r))
        assertThat(back).isEqualTo(r)
        assertThat(back.status.logged).isFalse()
    }

    @Test
    fun `validation refuses what the phone should not apply`() {
        fun req(kind: RequestKind, block: (RemoteRequest) -> RemoteRequest = { it }) = block(RemoteRequest("r", kind, 1))
        assertThat(req(RequestKind.WATER) { it.copy(volumeMl = 250.0) }.validate()).isNull()
        assertThat(req(RequestKind.WATER).validate()).isNotNull()
        assertThat(req(RequestKind.WATER) { it.copy(volumeMl = -5.0) }.validate()).isNotNull()
        assertThat(req(RequestKind.WATER) { it.copy(volumeMl = 99999.0) }.validate()).isNotNull()
        assertThat(req(RequestKind.QUICK_ADD) { it.copy(kcal = 300) }.validate()).isNull()
        assertThat(req(RequestKind.QUICK_ADD) { it.copy(kcal = 0) }.validate()).isNotNull()
        assertThat(req(RequestKind.LOG_FOOD) { it.copy(foodId = "f") }.validate()).isNull()
        assertThat(req(RequestKind.LOG_FOOD) { it.copy(foodId = "f", grams = 0.0) }.validate()).isNotNull()
        assertThat(req(RequestKind.LOG_FOOD).validate()).isNotNull()
        assertThat(req(RequestKind.LOG_TEMPLATE).validate()).isNotNull()
        assertThat(req(RequestKind.START_WORKOUT).validate()).isNull()
        assertThat(req(RequestKind.LOG_PLANNED).validate()).isNull()
        assertThat(RemoteRequest("", RequestKind.START_WORKOUT, 1).validate()).isNotNull()
    }

    @Test
    fun `request time is kept, clamped when ahead, and refused when very old`() {
        val now = 1_000_000_000L
        assertThat(RemoteRequest("r", RequestKind.WATER, now - 60_000).effectiveTime(now)).isEqualTo(now - 60_000)
        assertThat(RemoteRequest("r", RequestKind.WATER, now + 60_000).effectiveTime(now)).isEqualTo(now)
        assertThat(RemoteRequest("r", RequestKind.WATER, now + 10 * 60_000).effectiveTime(now)).isEqualTo(now)
        assertThat(RemoteRequest("r", RequestKind.WATER, now - RemoteProtocol.MAX_REQUEST_AGE_MS - 1).effectiveTime(now)).isNull()
    }

    @Test
    fun `slots follow the phone's boundaries`() {
        assertThat(WatchSlot.forHour(7)).isEqualTo(WatchSlot.BREAKFAST)
        assertThat(WatchSlot.forHour(10)).isEqualTo(WatchSlot.LUNCH)
        assertThat(WatchSlot.forHour(14)).isEqualTo(WatchSlot.LUNCH)
        assertThat(WatchSlot.forHour(15)).isEqualTo(WatchSlot.DINNER)
        assertThat(WatchSlot.forHour(20)).isEqualTo(WatchSlot.DINNER)
        assertThat(WatchSlot.forHour(21)).isEqualTo(WatchSlot.SNACK)
        assertThat(WatchSlot.forHour(2)).isEqualTo(WatchSlot.BREAKFAST)
    }

    @Test
    fun `volumes are shown in the phone's unit and the picker steps by it`() {
        assertThat(RemoteProtocol.formatVolume(500.0, "ml")).isEqualTo("500 ml")
        assertThat(RemoteProtocol.formatVolume(500.0, "fl oz")).isEqualTo("16.9 fl oz")
        assertThat(RemoteProtocol.formatVolume(236.588, "fl oz")).isEqualTo("8 fl oz")
        assertThat(RemoteProtocol.stepMl("ml")).isEqualTo(50.0)
        assertThat(RemoteProtocol.stepMl("fl oz")).isWithin(1e-9).of(29.5735295625)
        assertThat(RemoteProtocol.wholeMl(RemoteProtocol.stepMl("fl oz") * 12)).isEqualTo(355.0)
    }

    @Test
    fun `the ledger answers a repeated id with the first answer`() {
        val ledger = RequestLedger(capacity = 3)
        val first = RemoteResponse("a", ResponseStatus.DONE, "Logged 250 ml")
        ledger.record(first)
        assertThat(ledger["a"]).isEqualTo(first)
        assertThat(ledger["b"]).isNull()
    }

    @Test
    fun `the ledger forgets the oldest and survives encoding`() {
        val ledger = RequestLedger(capacity = 3)
        listOf("a", "b", "c", "d").forEach { ledger.record(RemoteResponse(it, ResponseStatus.DONE, it)) }
        assertThat(ledger["a"]).isNull()
        assertThat(ledger.size).isEqualTo(3)
        val back = RequestLedger.decode(ledger.encode(), capacity = 3)
        assertThat(back["d"]?.message).isEqualTo("d")
        assertThat(back["b"]).isNotNull()
        assertThat(RequestLedger.decode("not json")["x"]).isNull()
        assertThat(RequestLedger.decode(null).size).isEqualTo(0)
    }

    @Test
    fun `pending wording names the request`() {
        assertThat(pendingLine(RemoteRequest("r", RequestKind.WATER, 1, label = "+500 ml"))).isEqualTo("+500 ml: pending, will sync")
        assertThat(pendingLine(RemoteRequest("r", RequestKind.QUICK_ADD, 1))).isEqualTo("Calories: pending, will sync")
    }
}
