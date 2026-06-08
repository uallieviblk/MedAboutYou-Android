package com.uallsi.medaboutyou

import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.model.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the EMA JSON → [Medicine] mapping (port of `Medicine::from_json`). */
class MedicineParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(text: String): Medicine =
        Medicine.fromEmaJson(json.parseToJsonElement(text).jsonObject)

    @Test
    fun maps_core_fields_and_yes_no_flags() {
        val med = parse(
            """
            {
              "category": "Human",
              "name_of_medicine": "Aspirin Cardio",
              "ema_product_number": "EMEA/H/C/000917",
              "medicine_status": "Authorised",
              "international_non_proprietary_name_common_name": "acetylsalicylic acid",
              "active_substance": "acetylsalicylic acid",
              "atc_code_human": "B01AC06",
              "generic": "Yes",
              "orphan_medicine": "No",
              "prime_priority_medicine": "Yes",
              "medicine_url": "https://www.ema.europa.eu/x"
            }
            """.trimIndent()
        )
        assertEquals(Source.EMA, med.source)
        assertEquals("Aspirin Cardio", med.name)
        assertEquals("EMEA/H/C/000917", med.productNumber)
        assertEquals("EMEA/H/C/000917", med.extId)   // ext_id falls back to product number
        assertEquals("B01AC06", med.atcCode)
        assertTrue(med.isHuman)
        assertTrue(med.generic)
        assertFalse(med.orphan)
        assertTrue(med.prime)
    }

    @Test
    fun ext_id_falls_back_to_name_when_no_product_number() {
        val med = parse("""{ "name_of_medicine": "Foo", "category": "Human" }""")
        assertEquals("Foo", med.extId)
    }

    @Test
    fun missing_and_non_string_fields_are_safe() {
        val med = parse("""{ "name_of_medicine": "Bar", "generic": 1, "atc_code_human": null }""")
        assertEquals("Bar", med.name)
        assertFalse(med.generic)        // non-"Yes" → false
        assertEquals("", med.atcCode)   // null → empty
    }

    @Test
    fun atc_code_falls_back_to_vet_field() {
        val med = parse("""{ "name_of_medicine": "Vetmed", "category": "Veterinary", "atcvet_code_veterinary": "QJ01" }""")
        assertEquals("QJ01", med.atcCode)
        assertFalse(med.isHuman)
    }
}
