package com.zeroindex.intake.domain.food

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NameMatchRankTest {
    private val foods = listOf("Salmon fillet, baked", "Almond milk", "Peanut butter", "Roasted almonds", "Salmon")

    private fun rank(q: String) = NameMatchRank.filterAndRank(foods, q, { it })

    @Test fun `almo ranks word starts ahead of Salmon`() {
        assertThat(rank("almo")).containsExactly("Almond milk", "Roasted almonds", "Salmon fillet, baked", "Salmon").inOrder()
    }

    @Test fun `a name that begins with the word beats a later word`() {
        assertThat(rank("almo").take(2)).containsExactly("Almond milk", "Roasted almonds").inOrder()
    }

    @Test fun `order is kept inside a tier`() {
        val items = listOf("Zucchini bread", "Apple bread", "Bread roll")
        assertThat(NameMatchRank.filterAndRank(items, "bread", { it })).containsExactly("Bread roll", "Zucchini bread", "Apple bread").inOrder()
    }

    @Test fun `non matches are dropped and a blank query changes nothing`() {
        assertThat(rank("kiwi")).isEmpty()
        assertThat(NameMatchRank.filterAndRank(foods, "  ", { it })).isEqualTo(foods)
    }

    @Test fun `several words must all match, and a brand counts`() {
        assertThat(NameMatchRank.tier("Whole milk", "Organic Valley", "valley milk")).isEqualTo(2)
        assertThat(NameMatchRank.tier("Whole milk", "Organic Valley", "milk who")).isEqualTo(1)
        assertThat(NameMatchRank.tier("Whole milk", "", "valley")).isNull()
    }

    @Test fun `rank only reorders`() {
        assertThat(NameMatchRank.rank(listOf("Salmon", "Almond milk"), "almo", { it })).containsExactly("Almond milk", "Salmon").inOrder()
    }
}
