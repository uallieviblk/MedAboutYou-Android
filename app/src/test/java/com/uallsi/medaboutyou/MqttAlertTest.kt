// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import com.uallsi.medaboutyou.reminders.CaregiverAlert
import com.uallsi.medaboutyou.reminders.MqttAlerts
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the caregiver-alert CBOR payload schema and the topic-level sanitiser. */
class MqttAlertTest {

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `caregiver alert round-trips through CBOR with all five fields`() {
        val alert = CaregiverAlert(
            id = 42L,
            user = "Alice",
            timestamp = "2026-06-18T08:15:00",
            category = MqttAlerts.CATEGORY_MEDICATION_OVERDUE,
            text = "Aspirin dose overdue (08:00)",
        )
        val bytes = Cbor.encodeToByteArray(alert)
        assertTrue("CBOR payload should be non-empty", bytes.isNotEmpty())

        val decoded = Cbor.decodeFromByteArray<CaregiverAlert>(bytes)
        assertEquals(alert, decoded)
        assertEquals(1, decoded.category) // numeric category, not a string
    }

    @Test
    fun `topic level strips MQTT wildcards and separators`() {
        assertEquals("bob", MqttAlerts.sanitizeTopicLevel("  bob  "))
        assertEquals("a_b_c_d", MqttAlerts.sanitizeTopicLevel("a/b#c+d"))
        assertEquals("two_words", MqttAlerts.sanitizeTopicLevel("two words"))
        assertEquals("unknown", MqttAlerts.sanitizeTopicLevel("   "))
    }

    @Test
    fun `sanitised username never reintroduces a topic separator or wildcard`() {
        val out = MqttAlerts.sanitizeTopicLevel("x/y/#/+ z")
        assertFalse(out.any { it == '/' || it == '#' || it == '+' || it.isWhitespace() })
    }
}
