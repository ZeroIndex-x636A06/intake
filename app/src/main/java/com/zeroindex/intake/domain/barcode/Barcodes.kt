package com.zeroindex.intake.domain.barcode

/**
 * Retail barcode (GTIN) handling, pure Kotlin so it is unit tested. Scans and typed
 * numbers both pass through [normalize]: a wrong-digit read or a typo is rejected
 * here instead of becoming a lookup that can only fail.
 *
 * The canonical form is what Open Food Facts stores: UPC-A (12 digits) gets a
 * leading zero to become EAN-13; EAN-8 and GTIN-14 are left as they are.
 */
object Barcodes {

    private val VALID_LENGTHS = setOf(8, 12, 13, 14)

    fun digitsOnly(raw: String): String = raw.filter { it.isDigit() }

    /** The GTIN check digit: weights 3,1,3,1... from the digit next to the check digit. */
    fun hasValidCheckDigit(code: String): Boolean {
        if (code.length !in VALID_LENGTHS || !code.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until code.length - 1) {
            val digit = code[code.length - 2 - i] - '0'
            sum += digit * if (i % 2 == 0) 3 else 1
        }
        return (10 - sum % 10) % 10 == code.last() - '0'
    }

    /**
     * Expands an 8-digit UPC-E (number system 0 or 1) to the 12-digit UPC-A it
     * stands for. Null when [upcE] is not a well-formed UPC-E.
     */
    fun expandUpcE(upcE: String): String? {
        if (upcE.length != 8 || !upcE.all { it.isDigit() }) return null
        val system = upcE[0]
        if (system != '0' && system != '1') return null
        val b = upcE.substring(1, 7)
        val check = upcE[7]
        val body = when (b[5]) {
            '0', '1', '2' -> "${b[0]}${b[1]}${b[5]}0000${b[2]}${b[3]}${b[4]}"
            '3' -> "${b[0]}${b[1]}${b[2]}00000${b[3]}${b[4]}"
            '4' -> "${b[0]}${b[1]}${b[2]}${b[3]}00000${b[4]}"
            else -> "${b[0]}${b[1]}${b[2]}${b[3]}${b[4]}0000${b[5]}"
        }
        return "$system$body$check"
    }

    /**
     * The canonical barcode for a scan or a typed number, or null when it cannot be a
     * real one (wrong length, or the check digit does not add up). [isUpcE] says the
     * scanner reported the UPC-E symbology, where eight digits are not an EAN-8.
     */
    fun normalize(raw: String, isUpcE: Boolean = false): String? {
        val digits = digitsOnly(raw)
        val code = if (isUpcE && digits.length == 8) expandUpcE(digits) ?: return null else digits
        if (!hasValidCheckDigit(code)) return null
        return if (code.length == 12) "0$code" else code
    }

    /**
     * Every spelling a stored barcode might have: foods saved before normalising can
     * hold the 12-digit UPC-A form. Checked in order against the local table.
     */
    fun lookupForms(canonical: String): List<String> = buildList {
        add(canonical)
        if (canonical.length == 13 && canonical.startsWith("0")) add(canonical.substring(1))
    }

    /**
     * Picks the barcode to act on from what the scanner saw in one frame: the first
     * one that normalizes. Frames that read a digit wrong are skipped, not delivered.
     */
    fun firstValid(reads: List<ScannedRead>): String? =
        reads.firstNotNullOfOrNull { normalize(it.raw, it.isUpcE) }
}

/** One decoded symbol: its text and whether the scanner said it was UPC-E. */
data class ScannedRead(val raw: String, val isUpcE: Boolean)
