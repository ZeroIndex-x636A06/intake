package com.zeroindex.intake.domain.backup

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional passphrase encryption for backups that leave the phone
 * (amendment §2). The VPN protects the file in transit; this protects it at
 * rest on the server, so a compromised home server does not mean a leaked
 * health history.
 *
 * AES-256-GCM with a key from PBKDF2-HMAC-SHA256. Layout:
 * `INTAKE-ENC1` | salt (16) | iv (12) | ciphertext with tag.
 * GCM authenticates, so a wrong passphrase or a tampered file fails loudly
 * rather than producing garbage.
 */
object BackupCrypto {
    private val MAGIC = "INTAKE-ENC1".toByteArray(Charsets.US_ASCII)
    private const val SALT = 16
    private const val IV = 12
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    class WrongPassphrase : Exception("Wrong passphrase, or the file has been altered.")

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, passphrase: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT).also(random::nextBytes)
        val iv = ByteArray(IV).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
        require(isEncrypted(data)) { "Not an encrypted Intake backup" }
        var o = MAGIC.size
        val salt = data.copyOfRange(o, o + SALT).also { o += SALT }
        val iv = data.copyOfRange(o, o + IV).also { o += IV }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        return try {
            cipher.doFinal(data, o, data.size - o)
        } catch (_: AEADBadTagException) {
            throw WrongPassphrase()
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray) = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)).encoded,
        "AES",
    )
}
