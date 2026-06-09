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
        // A bare GTIN/EAN-13 that encodes an Italian pharma AIC.
        aicFromGtin(digits)?.let { return it }
        // Otherwise: a plain numeric code (likely the AIC itself) or free text.
        return if (digits.isNotEmpty() && digits == s) digits else s
    }

    /** Italian pharma GTIN contains "080" then the 9-digit AIC. */
    private fun aicFromGtin(gtin: String): String? {
        val idx = gtin.indexOf("080")
        if (idx >= 0 && gtin.length >= idx + 12) {
            val aic = gtin.substring(idx + 3, idx + 12)
            if (aic.length == 9 && aic.all { it.isDigit() }) return aic
        }
        return null
    }
}
