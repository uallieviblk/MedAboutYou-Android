// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.remote

/**
 * Turns a scanned package code into a best-effort AIFA search query. Italian
 * medicine boxes carry the 9-digit AIC, either as a linear barcode or inside the
 * EU FMD Datamatrix (a GS1 string whose `01` GTIN embeds the AIC: an Italian
 * pharma GTIN is `…080` + AIC(9) + check digit). We extract the AIC where we can,
 * otherwise we fall back to the raw value so the user can refine the search.
 */
object BarcodeLookup {

    fun toQuery(raw: String): String {
        val s = raw.trim()
        val digits = s.filter { it.isDigit() }

        // FMD Datamatrix: application identifier "01" followed by a 14-digit GTIN.
        if (s.startsWith("01") && digits.length >= 16) {
            aicFromGtin(digits.substring(2, 16))?.let { return it }
        }
        // A bare GTIN/EAN-13 scan that encodes an Italian pharma AIC. Only a
        // complete 13/14-digit code qualifies — running the heuristic over the
        // concatenated digits of a full GS1 string (GTIN + expiry + lot +
        // serial) could match "080" across field boundaries and produce a
        // garbage AIC.
        if (digits.length in 13..14 && digits == s) {
            aicFromGtin(digits)?.let { return it }
        }
        // Otherwise: a plain numeric code (likely the AIC itself) or free text.
        return if (digits.isNotEmpty() && digits == s) digits else s
    }

    /** Italian pharma GTIN contains "080" then the 9-digit AIC. */
    private fun aicFromGtin(gtin: String): String? {
        if (gtin.length !in 13..14 || !gs1CheckDigitOk(gtin)) return null
        val idx = gtin.indexOf("080")
        if (idx >= 0 && gtin.length - 1 >= idx + 12) { // AIC + the trailing check digit
            val aic = gtin.substring(idx + 3, idx + 12)
            if (aic.length == 9 && aic.all { it.isDigit() }) return aic
        }
        return null
    }

    /** Standard GS1 (EAN/GTIN) modulo-10 check digit. */
    private fun gs1CheckDigitOk(code: String): Boolean {
        if (code.length < 2 || !code.all { it.isDigit() }) return false
        var sum = 0
        code.dropLast(1).reversed().forEachIndexed { i, c ->
            sum += (c - '0') * if (i % 2 == 0) 3 else 1
        }
        return (10 - sum % 10) % 10 == code.last() - '0'
    }
}
