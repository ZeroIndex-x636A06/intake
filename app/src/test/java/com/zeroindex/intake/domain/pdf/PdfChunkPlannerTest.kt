package com.zeroindex.intake.domain.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfChunkPlannerTest {

    @Test
    fun `a large pdf becomes bounded requests that cover every page once, in order`() {
        // 40 pages of 300 KB: one request would be 12 MB.
        val sizes = List(40) { 300_000L }
        val chunks = PdfChunkPlanner.plan(sizes)

        assertThat(chunks.sumOf { it.pages }).isEqualTo(40)
        assertThat(chunks.first().from).isEqualTo(0)
        assertThat(chunks.last().to).isEqualTo(39)
        chunks.zipWithNext().forEach { (a, b) -> assertThat(b.from).isEqualTo(a.to + 1) }
        assertThat(chunks.all { it.pages <= PdfChunkPlanner.MAX_PAGES_PER_CHUNK }).isTrue()
        assertThat(chunks.all { it.estimatedBytes <= PdfChunkPlanner.MAX_CHUNK_BYTES }).isTrue()
        assertThat(chunks.maxOf { it.estimatedBytes }).isLessThan(sizes.sum() / 10)
    }

    @Test
    fun `the byte budget closes a chunk before the page limit does`() {
        val chunks = PdfChunkPlanner.plan(listOf(900_000, 900_000, 900_000, 100_000), maxPages = 3, maxBytes = 1_000_000)
        assertThat(chunks.map { it.pages }).containsExactly(1, 1, 2).inOrder()
    }

    @Test
    fun `one oversized page still gets a chunk of its own rather than blocking the rest`() {
        val chunks = PdfChunkPlanner.plan(listOf(100, 5_000_000, 100), maxPages = 2, maxBytes = 1_000_000)
        assertThat(chunks.map { it.from to it.to }).containsExactly(0 to 0, 1 to 1, 2 to 2).inOrder()
    }

    @Test
    fun `no pages means no chunks and a single page is one chunk`() {
        assertThat(PdfChunkPlanner.plan(emptyList())).isEmpty()
        assertThat(PdfChunkPlanner.plan(listOf(10))).containsExactly(PdfChunk(0, 0, 10))
    }

    @Test
    fun `chunks are labelled by page number for people`() {
        assertThat(PdfChunk(2, 3, 0).label).isEqualTo("pages 3-4")
        assertThat(PdfChunk(0, 0, 0).label).isEqualTo("page 1")
    }

    @Test
    fun `the size message names both sizes`() {
        val msg = PdfChunkPlanner.tooBigMessage(20L * 1024 * 1024)
        assertThat(msg).contains("20.0 MB")
        assertThat(msg).contains("15 MB")
    }
}
