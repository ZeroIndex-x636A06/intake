package com.zeroindex.intake.data.ai.menu

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertThrows
import org.junit.Test

class MenuMergeAndFetchTest {

    private fun item(
        name: String,
        kcal: Double? = 300.0,
        protein: Double? = null,
        allergens: List<String> = emptyList(),
        published: Boolean = true,
        ingredients: String = "",
    ) = ParsedItem(name, "", ingredients, kcal, protein, null, null, published, null, allergens, "")

    @Test
    fun `a row read twice across a page boundary is kept once, with the fuller copy`() {
        val merged = MenuMerge.merge(
            listOf(
                listOf(item("Reuben", protein = null), item("Gyro")),
                listOf(item("Reuben®", protein = 37.0, allergens = listOf("Egg", "Milk")), item("Turkey Sandwich")),
            ),
        )
        assertThat(merged.map { MenuMerge.normalise(it.name) }).containsExactly("reuben", "gyro", "turkey sandwich").inOrder()
        assertThat(merged.first().protein).isEqualTo(37.0)
        assertThat(merged.first().containsAllergens).containsExactly("Egg", "Milk")
    }

    @Test
    fun `allergen declarations are only ever added together`() {
        val merged = MenuMerge.merge(
            listOf(listOf(item("Gyro", allergens = listOf("Egg"))), listOf(item("Gyro", allergens = listOf("egg", "Soy", "Wheat")))),
        )
        assertThat(merged.single().containsAllergens.map { it.lowercase() }).containsExactly("egg", "soy", "wheat")
    }

    @Test
    fun `the same name with different calories stays as two items`() {
        val merged = MenuMerge.merge(listOf(listOf(item("Fries", kcal = 300.0)), listOf(item("Fries", kcal = 450.0))))
        assertThat(merged).hasSize(2)
    }

    @Test
    fun `blank names are dropped and order is that of the pdf`() {
        val merged = MenuMerge.merge(listOf(listOf(item("  "), item("B")), listOf(item("A"))))
        assertThat(merged.map { it.name }).containsExactly("B", "A").inOrder()
    }

    @Test
    fun `names compare without trademark marks, footnote symbols and case`() {
        assertThat(MenuMerge.normalise("  Arby’s®  Roast   Beef* †")).isEqualTo("arby’s roast beef")
    }

    // --- the download cap ----------------------------------------------------------------------------

    private val fetcher = MenuFetcher(OkHttpClient())

    @Test
    fun `a stream within the cap is read whole and progress is reported`() = runBlocking<Unit> {
        val seen = mutableListOf<Long>()
        val bytes = fetcher.readCapped(ByteArrayInputStream(ByteArray(50_000) { 1 }), 100_000, 50_000) { read, _ -> seen += read }
        assertThat(bytes.size).isEqualTo(50_000)
        assertThat(seen.last()).isEqualTo(50_000)
    }

    @Test
    fun `a stream past the cap stops one byte over so it can be refused without reading the rest`() = runBlocking<Unit> {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 1.also { served++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int { served += len; return len }
        }
        val bytes = fetcher.readCapped(endless, 1_000, null) { _, _ -> }
        assertThat(bytes.size).isEqualTo(1_001)
        assertThat(served).isLessThan(40_000L)
    }

    @Test
    fun `a picked file over fifteen megabytes is refused with the sizes in the message`() = runBlocking<Unit> {
        val huge = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray): Int = b.size
        }
        val e = assertThrows(MenuImportException::class.java) { runBlocking { readPdfStream(huge) } }
        assertThat(e.message).contains("15 MB")
    }

    @Test
    fun `cancelling stops a read between chunks`() {
        val slow = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = len
        }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                val job = launch(Dispatchers.Default) {
                    fetcher.readCapped(slow, Long.MAX_VALUE / 2, null) { _, _ -> }
                }
                delay(50)
                job.cancel()
                job.join()
                if (job.isCancelled) throw CancellationException("cancelled")
            }
        }
    }
}
