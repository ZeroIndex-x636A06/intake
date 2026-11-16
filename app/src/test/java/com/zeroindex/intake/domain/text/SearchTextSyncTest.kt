package com.zeroindex.intake.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextSyncTest {
    @Test
    fun `typing shows at once`() {
        val s = SearchTextSync()
        "protein".fold("") { acc, c -> (acc + c).also { s.edit(it) } }
        assertEquals("protein", s.text)
    }

    @Test
    fun `a late echo of an earlier word does not overwrite newer typing`() {
        val s = SearchTextSync()
        s.edit("pro"); s.edit("prot"); s.edit("prote")
        assertFalse(s.external("pro"))
        assertEquals("prote", s.text)
        s.edit("protei")
        assertFalse(s.external("prote"))
        assertFalse(s.external("protei"))
        assertEquals("protei", s.text)
    }

    @Test
    fun `an echo that skips values is still recognised`() {
        val s = SearchTextSync()
        s.edit("a"); s.edit("ab"); s.edit("abc")
        assertFalse(s.external("abc"))
        assertEquals("abc", s.text)
    }

    @Test
    fun `a change the view model made is taken`() {
        val s = SearchTextSync()
        s.edit("milk")
        s.external("milk")
        assertTrue(s.external(""))
        assertEquals("", s.text)
        assertTrue(s.external("oat milk"))
        assertEquals("oat milk", s.text)
    }

    @Test
    fun `a cleared box is not undone by its own echo`() {
        val s = SearchTextSync()
        s.edit("x"); s.external("x")
        s.edit("")
        assertFalse(s.external(""))
        s.edit("y"); s.external("y")
        assertTrue(s.external(""))
        assertEquals("", s.text)
    }
}
