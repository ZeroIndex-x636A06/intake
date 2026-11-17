package com.zeroindex.intake.data.ai.menu

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MenuNutritionMergeTest {
    private fun chart(name: String, status: AllergenStatus, stated: String = "", matches: String = "") = MenuItemEntity(
        id = "c-$name", sourceId = "S", restaurantName = "R", name = name, description = "chart", ingredientsText = stated,
        macrosPublished = false, allergenStatus = status, allergenMatches = matches, importedAt = 1, createdAt = 1, updatedAt = 1,
    )

    private fun nutrition(name: String, kcal: Double, status: AllergenStatus, stated: String = "", matches: String = "") = MenuItemEntity(
        id = "n-$name", sourceId = "tmp", restaurantName = "R", name = name, kcal = kcal, proteinG = 10.0, carbsG = 20.0, fatG = 5.0,
        ingredientsText = stated, macrosPublished = true, allergenStatus = status, allergenMatches = matches,
        importedAt = 2, createdAt = 2, updatedAt = 2,
    )

    @Test
    fun `nutrition comes from the nutrition file and allergens from both`() {
        val r = MenuNutritionMerge.merge(
            chart = listOf(chart("Big Mac®", AllergenStatus.CONTAINS, "Contains: Milk, Egg; May contain: Sesame", "milk")),
            nutrition = listOf(nutrition("Big Mac", 590.0, AllergenStatus.CLEAR, "Contains: Soy")),
            sourceId = "S", now = 9,
        )
        val m = r.items.single()
        assertThat(m.kcal).isEqualTo(590.0)
        assertThat(m.macrosPublished).isTrue()
        assertThat(m.sourceId).isEqualTo("S")
        assertThat(m.allergenStatus).isEqualTo(AllergenStatus.CONTAINS)
        assertThat(m.ingredientsText).contains("Milk")
        assertThat(m.ingredientsText).contains("Soy")
        assertThat(m.ingredientsText).contains("May contain: Sesame")
        assertThat(m.allergenMatches).isEqualTo("milk")
        assertThat(r.summary).isEqualTo("Matched 1 of 1 items.")
    }

    @Test
    fun `merging only raises a verdict, never clears one`() {
        val r = MenuNutritionMerge.merge(
            chart = listOf(chart("Fries", AllergenStatus.CHECK_LABEL)),
            nutrition = listOf(nutrition("Fries", 300.0, AllergenStatus.CLEAR)),
            sourceId = "S", now = 9,
        )
        assertThat(r.items.single().allergenStatus).isEqualTo(AllergenStatus.CHECK_LABEL)
        val r2 = MenuNutritionMerge.merge(
            chart = listOf(chart("Fries", AllergenStatus.CHECK_LABEL)),
            nutrition = listOf(nutrition("Fries", 300.0, AllergenStatus.MAY_CONTAIN)),
            sourceId = "S", now = 9,
        )
        assertThat(r2.items.single().allergenStatus).isEqualTo(AllergenStatus.MAY_CONTAIN)
    }

    @Test
    fun `items in only one file are kept and counted`() {
        val r = MenuNutritionMerge.merge(
            chart = listOf(chart("Big Mac", AllergenStatus.CHECK_LABEL), chart("Hash Brown", AllergenStatus.CHECK_LABEL)),
            nutrition = listOf(nutrition("Big Mac", 590.0, AllergenStatus.CLEAR), nutrition("McFlurry", 400.0, AllergenStatus.MAY_CONTAIN)),
            sourceId = "S", now = 9,
        )
        assertThat(r.items.map { it.name }).containsExactly("Big Mac", "McFlurry", "Hash Brown")
        val hash = r.items.single { it.name == "Hash Brown" }
        assertThat(hash.kcal).isNull()
        assertThat(hash.allergenStatus).isEqualTo(AllergenStatus.CHECK_LABEL)
        val mcflurry = r.items.single { it.name == "McFlurry" }
        assertThat(mcflurry.allergenStatus).isEqualTo(AllergenStatus.MAY_CONTAIN)
        assertThat(mcflurry.sourceId).isEqualTo("S")
        assertThat(r.matched).isEqualTo(1)
        assertThat(r.chartTotal).isEqualTo(2)
        assertThat(r.nutritionOnly).isEqualTo(1)
        assertThat(r.summary).isEqualTo("Matched 1 of 2 items.")
    }

    @Test
    fun `one size-less chart row gives its allergens to every size in the nutrition file`() {
        val r = MenuNutritionMerge.merge(
            chart = listOf(chart("Chocolate Milkshake", AllergenStatus.CONTAINS, "Contains: Milk")),
            nutrition = listOf(nutrition("Chocolate Milkshake Small", 300.0, AllergenStatus.CLEAR), nutrition("Chocolate Milkshake Large", 500.0, AllergenStatus.CLEAR)),
            sourceId = "S", now = 9,
        )
        assertThat(r.items).hasSize(2)
        assertThat(r.items.all { it.allergenStatus == AllergenStatus.CONTAINS }).isTrue()
        assertThat(r.matched).isEqualTo(1)
    }

    // --- the stream reader the app uses for a picked PDF ---------------------------------------------------

    /** Returns at most [chunk] bytes per read, the way a content provider's stream can. */
    private class Trickle(private val data: ByteArray, private val chunk: Int) : InputStream() {
        private val inner = ByteArrayInputStream(data)
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, minOf(len, chunk))
        override fun available(): Int = 0
    }

    @Test
    fun `a picked file arrives whole however small the reads are`() = runBlocking<Unit> {
        val bytes = ByteArray(200_000) { (it * 31).toByte() }
        for (chunk in listOf(1, 7, 4096, 65_536)) {
            val got = readPdfStream(Trickle(bytes, chunk))
            assertThat(got).isEqualTo(bytes)
        }
    }
}
