package com.zeroindex.intake.domain.pdf

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfDocumentTest {

    private fun latin1(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    /**
     * A small classic PDF: [pages] pages under one Pages node that carries the MediaBox and Resources (so they
     * must be inherited), a shared font, a padded content stream per page whose bytes contain the words
     * "endobj" and "5 0 obj" to trip a naive scanner, and a link annotation on page 1 pointing at the last page.
     */
    private fun classicPdf(pages: Int, pad: Int = 2_000): ByteArray {
        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(latin1(s))
        w("%PDF-1.4\n%âãÏÓ\n")
        val firstPage = 10
        val contents = 100
        val kids = (0 until pages).joinToString(" ") { "${firstPage + it} 0 R" }
        w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        w("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $pages /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> >>\nendobj\n")
        w("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")
        for (i in 0 until pages) {
            val annots = if (i == 0) " /Annots [ 200 0 R ]" else ""
            w("${firstPage + i} 0 obj\n<< /Type /Page /Parent 2 0 R /Contents ${contents + i} 0 R$annots >>\nendobj\n")
        }
        w("200 0 obj\n<< /Type /Annot /Subtype /Link /Dest [${firstPage + pages - 1} 0 R /Fit] >>\nendobj\n")
        for (i in 0 until pages) {
            val body = "BT /F1 12 Tf 72 700 Td (Page ${i + 1} text) Tj ET\n% endobj 5 0 obj fake\n" + "x".repeat(pad)
            w("${contents + i} 0 obj\n<< /Length ${body.length} >>\nstream\n$body\nendstream\nendobj\n")
        }
        w("trailer\n<< /Size 300 /Root 1 0 R >>\n%%EOF\n")
        return out.toByteArray()
    }

    /** The same document with the page tree packed into a Flate-compressed object stream, as modern PDFs do. */
    private fun objectStreamPdf(pages: Int): ByteArray {
        val firstPage = 10
        val kids = (0 until pages).joinToString(" ") { "${firstPage + it} 0 R" }
        val members = mutableListOf<Pair<Int, String>>()
        members += 2 to "<< /Type /Pages /Kids [$kids] /Count $pages /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> >>"
        for (i in 0 until pages) members += (firstPage + i) to "<< /Type /Page /Parent 2 0 R /Contents ${100 + i} 0 R >>"
        var offset = 0
        val header = StringBuilder()
        val bodies = StringBuilder()
        for ((n, text) in members) {
            header.append("$n $offset ")
            bodies.append(text).append('\n')
            offset += text.length + 1
        }
        val raw = latin1(header.toString() + bodies)
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val buf = ByteArray(raw.size + 64)
        val n = deflater.deflate(buf)
        deflater.end()
        val packed = buf.copyOf(n)

        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(latin1(s))
        w("%PDF-1.5\n")
        w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        w("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")
        w("50 0 obj\n<< /Type /ObjStm /N ${members.size} /First ${header.length} /Filter /FlateDecode /Length ${packed.size} >>\nstream\n")
        out.write(packed)
        w("\nendstream\nendobj\n")
        for (i in 0 until pages) {
            val body = "BT /F1 12 Tf (Packed page ${i + 1}) Tj ET"
            w("${100 + i} 0 obj\n<< /Length ${body.length} >>\nstream\n$body\nendstream\nendobj\n")
        }
        w("trailer\n<< /Size 300 /Root 1 0 R >>\n%%EOF\n")
        return out.toByteArray()
    }

    private fun text(b: ByteArray) = String(b, Charsets.ISO_8859_1)

    @Test
    fun `reads the page count and the pages in order`() {
        val doc = PdfDocument.parse(classicPdf(6))
        assertThat(doc.pageCount).isEqualTo(6)
    }

    @Test
    fun `extracting a run of pages keeps only those pages and stays a valid pdf`() {
        val original = classicPdf(6, pad = 5_000)
        val doc = PdfDocument.parse(original)
        val part = doc.extract(2, 3)

        val t = text(part)
        assertThat(t).startsWith("%PDF-")
        assertThat(t).contains("Page 3 text")
        assertThat(t).contains("Page 4 text")
        assertThat(t).doesNotContain("Page 1 text")
        assertThat(t).doesNotContain("Page 6 text")
        assertThat(part.size).isLessThan(original.size / 2)

        val again = PdfDocument.parse(part)
        assertThat(again.pageCount).isEqualTo(2)
    }

    @Test
    fun `inherited media box and resources are carried onto the extracted pages`() {
        val part = text(PdfDocument.parse(classicPdf(3)).extract(1, 1))
        assertThat(part).contains("/MediaBox [0 0 612 792]")
        assertThat(part).contains("/Font << /F1 3 0 R >>")
        assertThat(part).contains("/BaseFont /Helvetica")
    }

    @Test
    fun `links do not drag other pages into an extract`() {
        // Page 1 carries a link annotation to the last page; extracting page 1 must not include that page.
        val part = text(PdfDocument.parse(classicPdf(5)).extract(0, 0))
        assertThat(part).contains("Page 1 text")
        assertThat(part).doesNotContain("Page 5 text")
        assertThat(part).doesNotContain("/Annot")
    }

    @Test
    fun `binary stream data that looks like objects is not mistaken for objects`() {
        val doc = PdfDocument.parse(classicPdf(4))
        assertThat(doc.pageCount).isEqualTo(4)
        assertThat(text(doc.extract(0, 3))).contains("Page 4 text")
    }

    @Test
    fun `pages packed in a compressed object stream are found`() {
        val doc = PdfDocument.parse(objectStreamPdf(4))
        assertThat(doc.pageCount).isEqualTo(4)
        val part = text(doc.extract(1, 2))
        assertThat(part).contains("Packed page 2")
        assertThat(part).contains("Packed page 3")
        assertThat(part).doesNotContain("Packed page 4")
        assertThat(PdfDocument.parse(latin1(part)).pageCount).isEqualTo(2)
    }

    @Test
    fun `estimated page sizes track the content and feed the planner`() {
        val doc = PdfDocument.parse(classicPdf(5, pad = 40_000))
        val sizes = (0 until doc.pageCount).map(doc::estimatedBytes)
        assertThat(sizes.all { it > 40_000 }).isTrue()
        val chunks = PdfChunkPlanner.plan(sizes, maxPages = 2, maxBytes = 100_000)
        assertThat(chunks.map { it.pages }).containsExactly(2, 2, 1).inOrder()
        assertThat(chunks.sumOf { it.pages }).isEqualTo(5)
    }

    @Test
    fun `a file that is not a pdf, or is encrypted, is refused with a plain message`() {
        val notPdf = assertThrows(PdfUnsupportedException::class.java) { PdfDocument.parse(latin1("<html>menu</html>")) }
        assertThat(notPdf.message).contains("not a PDF")
        val encrypted = latin1("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Encrypt 9 0 R >>\n%%EOF\n")
        val e = assertThrows(PdfUnsupportedException::class.java) { PdfDocument.parse(encrypted) }
        assertThat(e.message).contains("password")
    }

    @Test
    fun `a few bytes of junk before the pdf marker are tolerated`() {
        val doc = PdfDocument.parse(latin1("\r\n  junk\n") + classicPdf(2))
        assertThat(doc.pageCount).isEqualTo(2)
    }

    @Test
    fun `a real pdf with no page tree says its structure could not be read, not that a page list is missing from a web page`() {
        val e = assertThrows(PdfUnsupportedException::class.java) { PdfDocument.parse(latin1("%PDF-1.7\n1 0 obj\n<< /Foo 1 >>\nendobj\n%%EOF\n")) }
        assertThat(e.message).contains("structure could not be read")
    }

    /**
     * Run against a real file when INTAKE_TEST_PDF names one (the Arby's nutrition PDF, downloaded by hand and never
     * committed). Skipped otherwise. Chunks are written to INTAKE_TEST_PDF_OUT when that is set, so they can be
     * checked with pdfinfo and pdftotext.
     */
    @Test
    fun `a real nutrition table pdf splits into small readable chunks`() {
        val path = System.getenv("INTAKE_TEST_PDF")
        assumeTrue("INTAKE_TEST_PDF not set", path != null && File(path).isFile)
        val bytes = File(path!!).readBytes()
        val doc = PdfDocument.parse(bytes)
        val chunks = PdfChunkPlanner.plan((0 until doc.pageCount).map(doc::estimatedBytes))
        assertThat(chunks.sumOf { it.pages }).isEqualTo(doc.pageCount)
        val outDir = System.getenv("INTAKE_TEST_PDF_OUT")?.let(::File)?.also { it.mkdirs() }
        chunks.forEachIndexed { i, c ->
            val part = doc.extract(c.from, c.to)
            assertThat(PdfDocument.parse(part).pageCount).isEqualTo(c.pages)
            assertThat(part.size).isLessThan(bytes.size + 2_000)
            outDir?.let { File(it, "chunk-$i.pdf").writeBytes(part) }
        }
    }
}
