package com.zeroindex.intake.domain.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {

    private val plain = """{"format":"intake-export","tables":{}}""".toByteArray()

    @Test
    fun `encrypts and decrypts back to the same bytes`() {
        val sealed = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        assertThat(BackupCrypto.isEncrypted(sealed)).isTrue()
        assertThat(String(sealed)).doesNotContain("intake-export")
        assertThat(BackupCrypto.decrypt(sealed, "correct horse".toCharArray())).isEqualTo(plain)
    }

    @Test
    fun `the same input encrypts differently every time`() {
        val a = BackupCrypto.encrypt(plain, "p".toCharArray())
        val b = BackupCrypto.encrypt(plain, "p".toCharArray())
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `a wrong passphrase or a flipped byte fails loudly`() {
        val sealed = BackupCrypto.encrypt(plain, "right".toCharArray())
        assertThrows(BackupCrypto.WrongPassphrase::class.java) { BackupCrypto.decrypt(sealed, "wrong".toCharArray()) }
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertThrows(BackupCrypto.WrongPassphrase::class.java) { BackupCrypto.decrypt(tampered, "right".toCharArray()) }
    }

    @Test
    fun `plain json is not mistaken for an encrypted backup`() {
        assertThat(BackupCrypto.isEncrypted(plain)).isFalse()
    }
}
