// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import com.uallsi.medaboutyou.data.remote.BarcodeLookup
import org.junit.Assert.assertEquals
import org.junit.Test

/** AIC extraction from scanned package codes (EAN/GTIN and FMD Datamatrix). */
class BarcodeLookupTest {

    /** Append the GS1 modulo-10 check digit to [body]. */
    private fun withCheckDigit(body: String): String {
        var sum = 0
        body.reversed().forEachIndexed { i, c -> sum += (c - '0') * if (i % 2 == 0) 3 else 1 }
        return body + ((10 - sum % 10) % 10)
    }

    @Test
    fun gtin14_with_embedded_aic_yields_the_aic() {
        // GTIN-14: indicator 0 · "080" · AIC(9) · check digit.
        val gtin = withCheckDigit("0080123456789")
        assertEquals("123456789", BarcodeLookup.toQuery(gtin))
    }

    @Test
    fun fmd_datamatrix_gtin_yields_the_aic() {
        val gtin = withCheckDigit("0080123456789")
        // AI 01 + GTIN-14, followed by further AIs (expiry 17, lot 10).
        assertEquals("123456789", BarcodeLookup.toQuery("01${gtin}172701311012AB34"))
    }

    @Test
    fun bad_check_digit_falls_back_to_raw_digits() {
        val gtin = withCheckDigit("0080123456789")
        val corrupted = gtin.dropLast(1) + ((gtin.last() - '0' + 1) % 10)
        assertEquals(corrupted, BarcodeLookup.toQuery(corrupted))
    }

    @Test
    fun gs1_payload_digits_never_produce_a_cross_field_aic() {
        // "080" straddling AI boundaries (here inside expiry+lot) must not be
        // mistaken for an AIC: a non-"01" GS1 string falls through as-is.
        val raw = "21SERIAL08012345678"
        assertEquals(raw, BarcodeLookup.toQuery(raw))
    }

    @Test
    fun plain_aic_passes_through() {
        assertEquals("042645024", BarcodeLookup.toQuery("042645024"))
    }

    @Test
    fun free_text_passes_through() {
        assertEquals("tachipirina", BarcodeLookup.toQuery("tachipirina"))
    }
}
