// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import com.uallsi.medaboutyou.data.local.MqttConfig
import com.uallsi.medaboutyou.reminders.MqttPublisher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Exercises [MqttPublisher.testConnection] — the Settings "Test connection" probe.
 * It has no Android dependency (in-memory persistence), so it runs on the JVM.
 *
 * The success case needs a broker on 127.0.0.1:1883 and is **skipped** (JUnit
 * `Assume`) when none is reachable, so CI without a broker stays green; the
 * failure case needs nothing.
 */
class MqttTestConnectionTest {

    private fun brokerReachable(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", 1883), 800) }
        true
    }.getOrDefault(false)

    @Test
    fun succeeds_against_a_running_broker() {
        assumeTrue("no local MQTT broker on 127.0.0.1:1883", brokerReachable())
        val result = runBlocking {
            MqttPublisher.testConnection(MqttConfig(host = "127.0.0.1", port = 1883), "junit-probe")
        }
        assertTrue("expected success but was ${result.exceptionOrNull()}", result.isSuccess)
    }

    @Test
    fun fails_when_nothing_is_listening() {
        // Port 1 has no broker → the probe must report failure, not hang.
        val result = runBlocking {
            MqttPublisher.testConnection(MqttConfig(host = "127.0.0.1", port = 1), "junit-probe")
        }
        assertTrue("expected failure", result.isFailure)
    }
}
