package com.zeroindex.intake.domain.template

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TemplateUndoTest {
    @Test
    fun `undo soft-deletes exactly the logged rows and no others`() = runBlocking {
        val deleted = mutableListOf<String>()
        TemplateUndo.softDeleteLogged(listOf("a", "b", "c")) { deleted += it }
        assertThat(deleted).containsExactly("a", "b", "c").inOrder()
        Unit
    }

    @Test
    fun `a repeated id is soft-deleted once and an empty log deletes nothing`() = runBlocking {
        val deleted = mutableListOf<String>()
        TemplateUndo.softDeleteLogged(listOf("a", "a")) { deleted += it }
        TemplateUndo.softDeleteLogged(emptyList()) { deleted += it }
        assertThat(deleted).containsExactly("a")
        Unit
    }
}
