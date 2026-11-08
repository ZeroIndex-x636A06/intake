package com.zeroindex.intake.domain.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import org.junit.Test

class EatOutFilterTest {
    private fun opt(venue: String, order: String, week: String? = null, deleted: Boolean = false) = EatOutOptionEntity(
        id = "$venue|$order", planWeekId = week, venueName = venue, whatToOrder = order,
        estimatedKcal = 400, estimatedProtein = 30, createdAt = 0, updatedAt = 0, deletedAt = if (deleted) 1L else null,
    )

    private val chickenSandwich = opt("Zaxby's", "Grilled chicken sandwich, no ranch")
    private val steakBowl = opt("Burrito spot", "Steak burrito bowl")
    private val general = opt("Wendy's", "Plain baked potato")
    private val thisWeek = opt("Corner deli", "Turkey club, no cheese", week = "w5")
    private val otherWeek = opt("Elsewhere", "Anything", week = "w6")
    private val all = listOf(chickenSandwich, steakBowl, general, thisWeek, otherWeek)

    @Test fun `this week's own options always show, other weeks never`() {
        val r = EatOutFilter.forWeek(all, "w5", "Beef", includeAll = true).map { it.option.venueName }
        assertThat(r).contains("Corner deli")
        assertThat(r).doesNotContain("Elsewhere")
    }

    @Test fun `null week options need a matching protein or none at all`() {
        val chicken = EatOutFilter.forWeek(all, "w5", "Chicken").map { it.option.venueName }
        assertThat(chicken).containsExactly("Corner deli", "Zaxby's", "Wendy's").inOrder()
        val beef = EatOutFilter.forWeek(all, "w5", "Beef").map { it.option.venueName }
        assertThat(beef).containsExactly("Corner deli", "Burrito spot", "Wendy's").inOrder()
    }

    @Test fun `steak counts as beef and salmon as fish`() {
        val r = EatOutFilter.forWeek(listOf(steakBowl, opt("Dock", "Salmon plate")), null, "Beef").map { it.option.venueName }
        assertThat(r).containsExactly("Burrito spot")
        val f = EatOutFilter.forWeek(listOf(opt("Dock", "Salmon plate")), null, "Fish").map { it.option.venueName }
        assertThat(f).containsExactly("Dock")
    }

    @Test fun `show all adds the hidden ones last`() {
        val r = EatOutFilter.forWeek(all, "w5", "Chicken", includeAll = true)
        assertThat(r.last().match).isEqualTo(EatOutFilter.Match.OTHER)
        assertThat(r.last().option.venueName).isEqualTo("Burrito spot")
    }

    @Test fun `deleted options never show and a blank protein keeps general ones`() {
        val r = EatOutFilter.forWeek(listOf(opt("Gone", "Plain potato", deleted = true), general), null, "")
        assertThat(r.map { it.option.venueName }).containsExactly("Wendy's")
    }

    @Test fun `no week at all still offers general options`() {
        assertThat(EatOutFilter.forWeek(all, null, null).map { it.option.venueName }).containsExactly("Wendy's")
    }
}
