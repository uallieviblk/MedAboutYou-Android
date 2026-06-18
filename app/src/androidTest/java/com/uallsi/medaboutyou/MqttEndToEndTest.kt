// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uallsi.medaboutyou.data.local.MqttConfig
import com.uallsi.medaboutyou.data.local.MqttOutboxEntity
import com.uallsi.medaboutyou.reminders.CaregiverAlert
import com.uallsi.medaboutyou.reminders.MqttAlerts
import com.uallsi.medaboutyou.reminders.MqttPublisher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttCallback
import org.eclipse.paho.mqttv5.client.MqttClient
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * **End-to-end** caregiver-alert delivery against a real MQTT broker.
 *
 * The app's real [MqttPublisher] publishes a queued alert; a second Paho client
 * subscribes and receives the bytes off the wire, which are decoded as the CBOR
 * [CaregiverAlert] and verified — proving QoS-2 delivery + the wire format.
 *
 * Needs a broker reachable at [BROKER]:[PORT] (the Android-emulator alias for the
 * host loopback). With none present the test **skips** (JUnit `Assume`), so it is
 * CI-safe. To run it: `mosquitto -c <conf with: listener 1883 0.0.0.0 /
 * allow_anonymous true>` on the host, then `connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MqttEndToEndTest {

    private val app get() = ApplicationProvider.getApplicationContext<MedApp>()

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun publish_delivers_a_cbor_alert_that_a_subscriber_decodes() = runBlocking {
        val topic = "medaboutyou/caregiver/carer"
        val received = ArrayBlockingQueue<ByteArray>(4)

        val sub = MqttClient("tcp://$BROKER:$PORT", "e2e-sub-" + UUID.randomUUID(), MemoryPersistence())
        try {
            sub.connect(MqttConnectionOptions().apply { isCleanStart = true; connectionTimeout = 5 })
        } catch (e: MqttException) {
            Assume.assumeNoException("no MQTT broker reachable at $BROKER:$PORT — skipping", e)
        }

        try {
            sub.setCallback(object : MqttCallback {
                override fun messageArrived(t: String?, message: MqttMessage?) {
                    message?.payload?.let { received.offer(it) }
                }
                override fun disconnected(response: MqttDisconnectResponse?) {}
                override fun mqttErrorOccurred(exception: MqttException?) {}
                override fun deliveryComplete(token: IMqttToken?) {}
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {}
                override fun authPacketArrived(reasonCode: Int, properties: MqttProperties?) {}
            })
            sub.subscribe(topic, 2)

            // Queue one alert and publish it through the app's real publisher.
            val dao = app.container.db.mqttOutboxDao()
            dao.pending().forEach { dao.delete(it.id) }
            val ts = LocalDateTime.now().toString()
            val id = dao.insert(
                MqttOutboxEntity(
                    topic = topic,
                    user = "Patient",
                    timestamp = ts,
                    category = MqttAlerts.CATEGORY_MEDICATION_OVERDUE,
                    text = "Metformina is overdue",
                    qos = 2,
                    createdAt = ts,
                ),
            )
            val delivered = MqttPublisher.publish(
                app, MqttConfig(host = BROKER, port = PORT), "e2e-pub-" + UUID.randomUUID(), dao.pending(),
            )
            assertEquals("publisher should confirm QoS-2 delivery", setOf(id), delivered)

            val bytes = received.poll(10, TimeUnit.SECONDS)
            assertNotNull("subscriber should receive the published alert", bytes)
            val alert = Cbor.decodeFromByteArray<CaregiverAlert>(bytes!!)
            assertEquals(id, alert.id)
            assertEquals("Patient", alert.user)
            assertEquals(MqttAlerts.CATEGORY_MEDICATION_OVERDUE, alert.category)
            assertTrue("alert text names the medicine", alert.text.contains("Metformina"))

            dao.pending().forEach { dao.delete(it.id) }
        } finally {
            runCatching { if (sub.isConnected) sub.disconnect() }
            runCatching { sub.close() }
        }
    }

    private companion object {
        const val BROKER = "10.0.2.2" // emulator alias for the host loopback
        const val PORT = 1883
    }
}
