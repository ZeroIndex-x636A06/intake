package com.zeroindex.intake.domain.hydration

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DrinksTest {

    private fun entry(type: DrinkType, ml: Double, override: DrinkOverride? = null) =
        Drinks.entry(ml, Drinks.profile(type, override))

    @Test
    fun `tea and coffee count in full like water`() {
        assertThat(entry(DrinkType.TEA, 250.0).effectiveMl).isEqualTo(250.0)
        assertThat(entry(DrinkType.COFFEE, 250.0).effectiveMl).isEqualTo(250.0)
        assertThat(entry(DrinkType.WATER, 250.0).effectiveMl).isEqualTo(250.0)
        assertThat(DrinkType.TEA.defaultFactor).isEqualTo(1.0)
        assertThat(DrinkType.COFFEE.defaultFactor).isEqualTo(1.0)
    }

    @Test
    fun `default factors follow the documented table`() {
        assertThat(DrinkType.MILK.defaultFactor).isEqualTo(1.5)
        assertThat(DrinkType.ORS.defaultFactor).isEqualTo(1.5)
        assertThat(DrinkType.JUICE.defaultFactor).isEqualTo(1.1)
        assertThat(DrinkType.SODA.defaultFactor).isEqualTo(1.0)
        assertThat(DrinkType.SPORTS_DRINK.defaultFactor).isEqualTo(1.1)
        assertThat(DrinkType.WINE.defaultFactor).isEqualTo(0.7)
    }

    @Test
    fun `beer depends on strength`() {
        assertThat(Drinks.profile(DrinkType.BEER).factor).isEqualTo(0.9)
        assertThat(Drinks.profile(DrinkType.BEER, DrinkOverride(abvPercent = 3.0)).factor).isEqualTo(1.0)
        assertThat(Drinks.profile(DrinkType.BEER, DrinkOverride(abvPercent = 3.5)).factor).isEqualTo(1.0)
        assertThat(Drinks.profile(DrinkType.BEER, DrinkOverride(abvPercent = 7.0)).factor).isEqualTo(0.9)
    }

    @Test
    fun `spirits take away the volume of pure alcohol`() {
        val shot = entry(DrinkType.SPIRITS, 44.0)
        assertThat(shot.alcoholMl).isWithin(1e-9).of(17.6)
        assertThat(shot.effectiveMl).isWithin(1e-9).of(-17.6)
    }

    @Test
    fun `a user edit wins over the default`() {
        val e = entry(DrinkType.CUSTOM, 500.0, DrinkOverride(factor = 1.3, caffeineMgPer250 = 80.0))
        assertThat(e.effectiveMl).isWithin(1e-9).of(650.0)
        assertThat(e.caffeineMg).isWithin(1e-9).of(160.0)
    }

    @Test
    fun `caffeine scales with the volume from mg per 250 ml`() {
        assertThat(entry(DrinkType.COFFEE, 250.0).caffeineMg).isEqualTo(95.0)
        assertThat(entry(DrinkType.COFFEE, 500.0).caffeineMg).isEqualTo(190.0)
        assertThat(entry(DrinkType.TEA, 250.0).caffeineMg).isEqualTo(47.0)
        assertThat(entry(DrinkType.SODA, 250.0).caffeineMg).isEqualTo(24.0)
        assertThat(entry(DrinkType.WATER, 1000.0).caffeineMg).isEqualTo(0.0)
    }

    @Test
    fun `a standard drink is 14 g of alcohol`() {
        // 355 ml of 5% beer = 17.75 ml alcohol = 14.0 g.
        val beer = entry(DrinkType.BEER, 355.0)
        assertThat(beer.standardDrinks).isWithin(0.02).of(1.0)
        // 148 ml of 12% wine and 44 ml of 40% spirits are also about one.
        assertThat(entry(DrinkType.WINE, 148.0).standardDrinks).isWithin(0.02).of(1.0)
        assertThat(entry(DrinkType.SPIRITS, 44.0).standardDrinks).isWithin(0.02).of(1.0)
    }

    @Test
    fun `overrides survive a round trip and ignore junk`() {
        val map = mapOf(
            DrinkType.TEA to DrinkOverride(caffeineMgPer250 = 28.0),
            DrinkType.BEER to DrinkOverride(factor = 1.0, abvPercent = 4.2),
        )
        assertThat(DrinkOverrides.decode(DrinkOverrides.encode(map))).isEqualTo(map)
        assertThat(DrinkOverrides.decode("NOPE=1||;TEA;WINE=||")).isEmpty()
        assertThat(DrinkOverrides.decode(null)).isEmpty()
        assertThat(DrinkOverrides.encode(mapOf(DrinkType.TEA to DrinkOverride()))).isEmpty()
    }
}

class CaffeineAlcoholTest {

    @Test
    fun `caffeine top up starts at 300 mg and rises above 400`() {
        assertThat(CaffeineAlcohol.caffeineTopUpMl(299.0)).isEqualTo(0.0)
        assertThat(CaffeineAlcohol.caffeineTopUpMl(300.0)).isEqualTo(100.0)
        assertThat(CaffeineAlcohol.caffeineTopUpMl(400.0)).isEqualTo(100.0)
        assertThat(CaffeineAlcohol.caffeineTopUpMl(401.0)).isEqualTo(200.0)
    }

    @Test
    fun `alcohol adds 200 ml per standard drink up to a litre`() {
        assertThat(CaffeineAlcohol.alcoholTopUpMl(0.0)).isEqualTo(0.0)
        assertThat(CaffeineAlcohol.alcoholTopUpMl(2.0)).isEqualTo(400.0)
        assertThat(CaffeineAlcohol.alcoholTopUpMl(12.0)).isEqualTo(1_000.0)
    }

    @Test
    fun `notes are gentle and only appear past the thresholds`() {
        val calm = DayDrinkTotals(1000.0, 1000.0, 190.0, 0.0, 95.0)
        assertThat(CaffeineAlcohol.notes(calm)).isEmpty()

        val high = DayDrinkTotals(2000.0, 2000.0, 450.0, 0.0, 95.0)
        val notes = CaffeineAlcohol.notes(high)
        assertThat(notes).hasSize(1)
        assertThat(notes.single()).contains("400 mg")
        assertThat(notes.single().lowercase()).doesNotContain("dehydrat")

        val bigServing = DayDrinkTotals(500.0, 500.0, 250.0, 0.0, 250.0)
        assertThat(CaffeineAlcohol.notes(bigServing).single()).contains("200 mg")
    }

    @Test
    fun `the copy never says tea or coffee dehydrate`() {
        val all = listOf(
            CaffeineAlcohol.TEA_COFFEE_COUNT,
            CaffeineAlcohol.CAFFEINE_TOPUP_WHY,
            CaffeineAlcohol.ALCOHOL_TOPUP_WHY,
        ) + CaffeineAlcohol.notes(DayDrinkTotals(1.0, 1.0, 900.0, 3.0, 300.0))
        all.forEach { assertThat(it.lowercase()).doesNotContain("dehydrat") }
        assertThat(CaffeineAlcohol.TEA_COFFEE_COUNT).contains("count toward your water")
    }

    @Test
    fun `spirits never push the day total below zero`() {
        val shots = List(5) { Drinks.entry(44.0, Drinks.profile(DrinkType.SPIRITS)) }
        val t = CaffeineAlcohol.totals(shots)
        assertThat(t.effectiveMl).isEqualTo(0.0)
        assertThat(t.rawMl).isEqualTo(220.0)
        assertThat(t.standardDrinks).isWithin(0.05).of(5.0)
    }

    @Test
    fun `negative entries still reduce a positive total`() {
        val entries = listOf(
            Drinks.entry(500.0, Drinks.profile(DrinkType.WATER)),
            Drinks.entry(44.0, Drinks.profile(DrinkType.SPIRITS)),
        )
        assertThat(CaffeineAlcohol.totals(entries).effectiveMl).isWithin(1e-9).of(482.4)
    }
}

class WeatherAdjustmentTest {
    @Test
    fun `bands`() {
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(24.9))).isEqualTo(0.0)
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(25.0))).isEqualTo(250.0)
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(29.9))).isEqualTo(250.0)
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(30.0))).isEqualTo(500.0)
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(35.0))).isEqualTo(500.0)
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(35.1))).isEqualTo(750.0)
    }

    @Test
    fun `humidity bump only when hot and humid`() {
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(33.0, 80.0))).isEqualTo(750.0)
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(33.0, 50.0))).isEqualTo(500.0)
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(33.0, null))).isEqualTo(500.0)
        // Humid but not above 27 C: no bump (and 26 C adds the plain band only).
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(26.0, 95.0))).isEqualTo(250.0)
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(20.0, 95.0))).isEqualTo(0.0)
    }

    @Test
    fun `capped at one litre`() {
        assertThat(WeatherAdjustment.adjustmentMl(WeatherDay(45.0, 99.0))).isEqualTo(1_000.0)
    }

    @Test
    fun `coordinates round to one decimal`() {
        assertThat(CoarseCoordinates.round(30.26715)).isEqualTo(30.3)
        assertThat(CoarseCoordinates.round(-97.74306)).isEqualTo(-97.7)
        assertThat(CoarseCoordinates.round(-0.04)).isEqualTo(0.0)
        assertThat(CoarseCoordinates.round(51.4999)).isEqualTo(51.5)
    }
}

class SweatLossTest {
    @Test
    fun `intensity from average effort`() {
        assertThat(SweatLoss.intensityFor(null)).isEqualTo(SweatIntensity.MODERATE)
        assertThat(SweatLoss.intensityFor(5.5)).isEqualTo(SweatIntensity.LIGHT)
        assertThat(SweatLoss.intensityFor(6.0)).isEqualTo(SweatIntensity.MODERATE)
        assertThat(SweatLoss.intensityFor(7.9)).isEqualTo(SweatIntensity.MODERATE)
        assertThat(SweatLoss.intensityFor(8.0)).isEqualTo(SweatIntensity.HARD)
    }

    @Test
    fun `minutes times the rate`() {
        // 60 min of moderate = 0.8 L.
        assertThat(SweatLoss.totalMl(listOf(SessionLoad(60.0, null)), null)).isWithin(1e-9).of(800.0)
        // 45 min hard = 0.75 h x 1.2 = 0.9 L.
        assertThat(SweatLoss.totalMl(listOf(SessionLoad(45.0, 9.0)), null)).isWithin(1e-9).of(900.0)
    }

    @Test
    fun `a measured rate replaces the defaults`() {
        assertThat(SweatLoss.totalMl(listOf(SessionLoad(60.0, 9.0)), 1.5)).isWithin(1e-9).of(1_500.0)
    }

    @Test
    fun `tiny sessions are ignored and the day is capped`() {
        assertThat(SweatLoss.totalMl(listOf(SessionLoad(3.0, null)), null)).isEqualTo(0.0)
        assertThat(SweatLoss.totalMl(listOf(SessionLoad(240.0, 9.0)), null)).isEqualTo(2_000.0)
    }

    @Test
    fun `measuring a sweat rate`() {
        // 80.0 -> 79.2 kg, drank 0.5 L over 60 minutes: (0.8 + 0.5) / 1 h = 1.3 L/h.
        val m = SweatMeasurement(80.0, 79.2, 500.0, 60.0)
        assertThat(m.litresPerHour!!).isWithin(1e-9).of(1.3)
        val working = m.calculation({ "%.1f kg".format(it) }, { "%.2f L".format(it) })!!
        assertThat(working).contains("1.30 L over 1.00 h")
        assertThat(working).contains("1.30 L/h")
    }

    @Test
    fun `implausible measurements give no rate`() {
        assertThat(SweatMeasurement(80.0, 79.0, 0.0, 5.0).litresPerHour).isNull() // too short
        assertThat(SweatMeasurement(80.0, 85.0, 0.0, 60.0).litresPerHour).isNull() // gained weight
        assertThat(SweatMeasurement(80.0, 70.0, 0.0, 60.0).litresPerHour).isNull() // 10 L/h
        assertThat(SweatMeasurement(0.0, 70.0, 0.0, 60.0).litresPerHour).isNull()
    }
}

class HydrationPlanTest {
    private val none = DayDrinkTotals.EMPTY

    @Test
    fun `no weight and nothing else is the fallback base`() {
        val b = HydrationPlan.compose(null, null, dynamic = true)
        assertThat(b.totalMl).isEqualTo(HydrationTarget.FALLBACK_BASE_ML)
        assertThat(b.adjustments).isEmpty()
        assertThat(b.base.why).contains("until you log a weight")
    }

    @Test
    fun `the old compute and the new plan agree on a plain training day`() {
        val old = HydrationTarget.compute(null, 80.0, dynamic = true, trainedToday = true, activeKcalNet = null)
        val new = HydrationPlan.compose(null, 80.0, dynamic = true, plannedTrainingDay = true)
        assertThat(new.totalMl).isEqualTo(old.totalMl)
        assertThat(new.toTarget().reasons).isEqualTo(old.reasons)
    }

    @Test
    fun `a logged workout replaces the flat training day bonus`() {
        val b = HydrationPlan.compose(
            null, 80.0, dynamic = true, plannedTrainingDay = true,
            sessions = listOf(SessionLoad(60.0, 9.0)),
        )
        // 60 min hard = 1,200 ml, not the flat 500.
        assertThat(b.adjustments.map { it.id }).containsExactly(TargetLine.Id.SWEAT)
        assertThat(b.adjustments.single().ml).isEqualTo(1_200.0)
        assertThat(b.totalMl).isEqualTo(2_800.0 + 1_200.0)
    }

    @Test
    fun `a session with no time yet falls back to the flat bonus`() {
        val b = HydrationPlan.compose(
            null, 80.0, dynamic = true, plannedTrainingDay = true,
            sessions = listOf(SessionLoad(1.0, null)),
        )
        assertThat(b.adjustments.single().id).isEqualTo(TargetLine.Id.TRAINING)
    }

    @Test
    fun `active calories only add what is beyond the workout sweat`() {
        val b = HydrationPlan.compose(
            null, 80.0, dynamic = true,
            sessions = listOf(SessionLoad(60.0, null)), // 800 ml
            activeKcalNet = 1_000.0, // 1,000 ml of activity, cap 1,000
        )
        assertThat(b.adjustments.first { it.id == TargetLine.Id.ACTIVITY }.ml).isEqualTo(200.0)
        val covered = HydrationPlan.compose(
            null, 80.0, dynamic = true,
            sessions = listOf(SessionLoad(60.0, 9.0)), // 1,200 ml
            activeKcalNet = 700.0,
        )
        assertThat(covered.adjustments.any { it.id == TargetLine.Id.ACTIVITY }).isFalse()
    }

    @Test
    fun `weather adds a labelled line and none without a forecast`() {
        val hot = HydrationPlan.compose(null, 80.0, dynamic = true, weather = WeatherDay(33.0, 40.0))
        val line = hot.adjustments.single()
        assertThat(line.id).isEqualTo(TargetLine.Id.WEATHER)
        assertThat(line.ml).isEqualTo(500.0)
        assertThat(line.short).isEqualTo("33 °C today")
        val none = HydrationPlan.compose(null, 80.0, dynamic = true, weather = null)
        assertThat(none.adjustments).isEmpty()
        val mild = HydrationPlan.compose(null, 80.0, dynamic = true, weather = WeatherDay(18.0, 90.0))
        assertThat(mild.adjustments).isEmpty()
    }

    @Test
    fun `caffeine and alcohol top ups join the target`() {
        val totals = DayDrinkTotals(rawMl = 3000.0, effectiveMl = 3000.0, caffeineMg = 450.0, standardDrinks = 2.0, largestCaffeineServingMg = 95.0)
        val b = HydrationPlan.compose(null, 80.0, dynamic = true, drinks = totals)
        assertThat(b.adjustments.map { it.id }).containsExactly(TargetLine.Id.CAFFEINE, TargetLine.Id.ALCOHOL).inOrder()
        assertThat(b.adjustments[0].ml).isEqualTo(200.0)
        assertThat(b.adjustments[1].ml).isEqualTo(400.0)
        assertThat(b.adjustments[0].why).contains("Large amounts of caffeine can increase urine output a little")
        assertThat(b.totalMl).isEqualTo(2_800.0 + 600.0)
    }

    @Test
    fun `the full composition adds up line by line`() {
        val b = HydrationPlan.compose(
            manualBaseMl = null,
            bodyweightKg = 70.0,
            dynamic = true,
            sessions = listOf(SessionLoad(45.0, 7.0)),
            weather = WeatherDay(31.0, 75.0),
            drinks = DayDrinkTotals(2000.0, 2000.0, 320.0, 0.0, 95.0),
        )
        // base 2,450; sweat 45 min x 0.8 = 600; weather 500 + 250 humid = 750; caffeine 100.
        assertThat(b.baseMl).isEqualTo(2_450.0)
        assertThat(b.adjustments.map { it.ml }).containsExactly(600.0, 750.0, 100.0).inOrder()
        assertThat(b.totalMl).isEqualTo(3_900.0)
        assertThat(b.toTarget().totalMl).isEqualTo(3_900.0)
    }

    @Test
    fun `with adjustments off the target is the base alone`() {
        val b = HydrationPlan.compose(
            null, 80.0, dynamic = false,
            sessions = listOf(SessionLoad(60.0, 9.0)),
            weather = WeatherDay(40.0, 90.0),
            drinks = DayDrinkTotals(1.0, 1.0, 900.0, 5.0, 300.0),
        )
        assertThat(b.adjustments).isEmpty()
        assertThat(b.totalMl).isEqualTo(2_800.0)
    }

    @Test
    fun `an unused total is never negative`() {
        assertThat(none.effectiveMl).isEqualTo(0.0)
    }
}
