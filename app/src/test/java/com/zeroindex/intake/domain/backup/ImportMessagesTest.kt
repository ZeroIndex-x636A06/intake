package com.zeroindex.intake.domain.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ImportMessagesTest {

    @Test
    fun `a cut-off backup is called damaged, not not-JSON`() {
        val cut = """{"format":"intake-export","formatVersion":1,"tables":{"food":[{"id":"a","na"""
        assertThat(ImportMessages.notJson(cut)).isEqualTo(ImportMessages.DAMAGED)
    }

    @Test
    fun `a random file is not an Intake backup`() {
        assertThat(ImportMessages.notJson("hello world")).isEqualTo(ImportMessages.NOT_BACKUP)
        assertThat(ImportMessages.notJson("")).isEqualTo(ImportMessages.NOT_BACKUP)
    }

    @Test
    fun `a bad number names the field and hides the serializer text`() {
        val raw = "Failed to parse literal '\"abc\"' as a double value at path: \$.weightKg\nJSON input: ....m\":36.5,\"createdAt\":1791813000000"
        val msg = ImportMessages.badTable("body_metric", raw)
        assertThat(msg).contains("body metric")
        assertThat(msg).contains("weightKg")
        assertThat(msg).doesNotContain("Failed to parse")
        assertThat(msg).doesNotContain("JSON input")
        assertThat(msg).doesNotContain("abc")
    }

    @Test
    fun `a missing field is named`() {
        val raw = "Field 'name' is required for type with serial name 'x.Food', but it was missing at path: \$"
        assertThat(ImportMessages.badTable("food", raw)).contains("\"name\"")
    }

    @Test
    fun `nested paths use the last field and unknown text falls back to a generic sentence`() {
        assertThat(ImportMessages.fieldName("bad at path: \$.items[2].grams")).isEqualTo("grams")
        val msg = ImportMessages.badTable("food", "Unexpected JSON token at offset 12: weird")
        assertThat(msg).doesNotContain("offset")
        assertThat(msg).contains("food")
        assertThat(ImportMessages.badTable("food", null)).contains("food")
    }

    @Test
    fun `the newer-version message gives the action`() {
        assertThat(ImportMessages.newerVersion()).contains("newer version")
        assertThat(ImportMessages.newerVersion()).contains("Update")
    }

    @Test
    fun `a truncated encrypted file is reported as truncated, not a crash`() {
        val sealed = BackupCrypto.encrypt("{}".toByteArray(), "pw".toCharArray())
        val cut = sealed.copyOfRange(0, 30)
        assertThat(BackupCrypto.isEncrypted(cut)).isTrue()
        assertThrows(BackupCrypto.Truncated::class.java) { BackupCrypto.decrypt(cut, "pw".toCharArray()) }
    }
}
