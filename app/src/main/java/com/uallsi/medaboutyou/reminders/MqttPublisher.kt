// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import android.content.Context
import com.uallsi.medaboutyou.data.local.MqttConfig
import com.uallsi.medaboutyou.data.local.MqttOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import org.eclipse.paho.mqttv5.client.MqttClient
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
import org.eclipse.paho.mqttv5.client.persist.MqttDefaultFilePersistence
import org.eclipse.paho.mqttv5.common.MqttMessage
import java.io.File

/**
 * Publishes queued caregiver alerts to the configured MQTT broker with confirmed
 * (QoS-2) delivery. The synchronous Paho v5 client is driven from a worker
 * thread; file persistence + a resumed session (cleanStart=false, no session
 * expiry) carry in-flight QoS state across reconnects. Returns the alert ids
 * actually confirmed delivered.
 */
object MqttPublisher {

    // MQTT 5 max session-expiry (0xFFFFFFFF) = the session never expires.
    private const val NEVER_EXPIRE = 4_294_967_295L
    private const val KEEP_ALIVE_SEC = 30
    private const val CONNECT_TIMEOUT_SEC = 15

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun publish(
        context: Context,
        config: MqttConfig,
        clientId: String,
        items: List<MqttOutboxEntity>,
    ): Set<Long> = withContext(Dispatchers.IO) {
        val scheme = if (config.tls) "ssl" else "tcp"
        val serverUri = "$scheme://${config.host}:${config.port}"
        val persistenceDir = File(context.filesDir, "mqtt").apply { mkdirs() }
        val client = MqttClient(serverUri, clientId, MqttDefaultFilePersistence(persistenceDir.absolutePath))
        val options = MqttConnectionOptions().apply {
            isCleanStart = false
            sessionExpiryInterval = NEVER_EXPIRE
            isAutomaticReconnect = true
            keepAliveInterval = KEEP_ALIVE_SEC
            connectionTimeout = CONNECT_TIMEOUT_SEC
            if (config.username.isNotBlank()) {
                userName = config.username
                password = config.password.toByteArray()
            }
            if (config.tls) {
                socketFactory = MqttTls.socketFactory(config.caCertPem, config.clientCertPem, config.clientKeyPem)
            }
        }
        val delivered = mutableSetOf<Long>()
        try {
            client.connect(options)
            for (item in items) {
                val payload: ByteArray = Cbor.encodeToByteArray(
                    CaregiverAlert(item.id, item.user, item.timestamp, item.category, item.text),
                )
                val message = MqttMessage(payload).apply {
                    qos = item.qos
                    isRetained = true
                }
                client.publish(item.topic, message) // blocks until the QoS handshake completes
                delivered += item.id
            }
        } finally {
            runCatching { if (client.isConnected) client.disconnect() }
            runCatching { client.close() }
        }
        delivered
    }
}
