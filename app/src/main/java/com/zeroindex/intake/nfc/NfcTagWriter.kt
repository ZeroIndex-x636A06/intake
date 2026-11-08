package com.zeroindex.intake.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

sealed interface TagWriteResult {
    data class Written(val uid: String) : TagWriteResult
    data object ReadOnly : TagWriteResult
    data class TooSmall(val needed: Int, val available: Int) : TagWriteResult
    data object Unsupported : TagWriteResult
    data class Failed(val reason: String) : TagWriteResult
}

/**
 * Writes a meal-template link to a tag (amendment §2): a URI record with the
 * link, plus an Android Application Record so tapping the tag opens Intake
 * even when another app would also claim the URI.
 *
 * Runs on the NFC binder thread - the reader-mode callback - never on main.
 */
object NfcTagWriter {

    fun message(uri: String, packageName: String) = NdefMessage(
        arrayOf(NdefRecord.createUri(uri), NdefRecord.createApplicationRecord(packageName)),
    )

    fun write(tag: Tag, uri: String, packageName: String): TagWriteResult {
        val message = message(uri, packageName)
        val uid = tag.id.joinToString("") { "%02X".format(it) }
        return try {
            Ndef.get(tag)?.use { ndef ->
                ndef.connect()
                when {
                    !ndef.isWritable -> TagWriteResult.ReadOnly
                    ndef.maxSize < message.byteArrayLength -> TagWriteResult.TooSmall(message.byteArrayLength, ndef.maxSize)
                    else -> {
                        ndef.writeNdefMessage(message)
                        TagWriteResult.Written(uid)
                    }
                }
            } ?: NdefFormatable.get(tag)?.use { formatable ->
                // A blank tag: format it with the message in one step.
                formatable.connect()
                formatable.format(message)
                TagWriteResult.Written(uid)
            } ?: TagWriteResult.Unsupported
        } catch (e: IOException) {
            TagWriteResult.Failed("The tag moved away before writing finished. Hold it still and try again.")
        } catch (e: FormatException) {
            TagWriteResult.Failed("The tag could not be formatted.")
        } catch (e: SecurityException) {
            TagWriteResult.Failed("The tag went out of range. Try again.")
        }
    }
}
