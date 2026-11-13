package com.zeroindex.intake.domain.template

import com.zeroindex.intake.domain.template.PickItem.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatePickerTest {

    private fun food(id: String, name: String, at: Long? = null, search: String = "") = PickItem(Kind.FOOD, id, name, "d", at, search)
    private fun recipe(id: String, name: String, at: Long? = null) = PickItem(Kind.RECIPE, id, name, "d", at)

    private val saved = listOf(
        food("1", "Oats", at = 100),
        food("2", "banana"),
        recipe("3", "Chili", at = 300),
        food("4", "Apple", at = 200),
        recipe("5", "Zucchini bake"),
    )

    @Test fun emptySearchListsRecentThenEverythingAlphabetical() {
        val l = TemplatePicker.build("", saved)
        assertEquals(listOf("3", "4", "1"), l.recent.map { it.id })
        assertEquals(listOf("Apple", "banana", "Chili", "Oats", "Zucchini bake"), l.allSaved.map { it.name })
    }

    @Test fun noRecentsShowsOnlyAllSaved() {
        val l = TemplatePicker.build("", saved.map { it.copy(lastUsedAt = null) })
        assertTrue(l.recent.isEmpty())
        assertEquals(5, l.allSaved.size)
    }

    @Test fun recentIsCappedAndMixesFoodsAndRecipes() {
        val many = (1..12).map { food("f$it", "Food $it", at = it.toLong()) }
        val l = TemplatePicker.build("", many, recentLimit = 5)
        assertEquals(listOf("f12", "f11", "f10", "f9", "f8"), l.recent.map { it.id })
        assertEquals(12, l.allSaved.size)
    }

    @Test fun typingFiltersBothLists() {
        val l = TemplatePicker.build("ap", saved)
        assertEquals(listOf("Apple"), l.recent.map { it.name })
        assertEquals(listOf("Apple"), l.allSaved.map { it.name })
    }

    @Test fun typingRanksBetterMatchesFirstAndIgnoresCase() {
        val items = listOf(food("1", "Pineapple"), food("2", "apple"), food("3", "Apple pie"))
        val l = TemplatePicker.build("APPLE", items)
        assertEquals(listOf("2", "3", "1"), l.allSaved.map { it.id })
    }

    @Test fun typingMatchesBrandWordsButAfterNameMatches() {
        val items = listOf(food("1", "Cereal", search = "Kelloggs"), food("2", "Kelloggs bar"))
        val l = TemplatePicker.build("kelloggs", items)
        assertEquals(listOf("2", "1"), l.allSaved.map { it.id })
    }

    @Test fun nothingMatchingGivesEmptyLists() {
        val l = TemplatePicker.build("xyz", saved)
        assertTrue(l.recent.isEmpty() && l.allSaved.isEmpty())
    }

    @Test fun recentOutsideTheWindowIsNotPromotedByTyping() {
        val items = listOf(food("a", "Rice", at = 1)) + (2..9).map { food("f$it", "Other $it", at = it.toLong()) }
        val l = TemplatePicker.build("rice", items, recentLimit = 3)
        assertTrue(l.recent.isEmpty())
        assertEquals(listOf("a"), l.allSaved.map { it.id })
    }
}
