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
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
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
    private const val TEST_TIMEOUT_SEC = 10

    /**
     * One-shot connectivity probe for the Settings "Test connection" action. Opens
     * an **isolated** session — a unique client id, `cleanStart`, no session expiry,
     * in-memory persistence and fail-fast (no auto-reconnect) — so it never disturbs
     * the durable delivery session or its file-persisted in-flight QoS state, then
     * disconnects. [Result.success] means the broker accepted the connection: the
     * host/port is reachable and the TLS handshake + auth (if any) succeeded.
     */
    suspend fun testConnection(config: MqttConfig, clientId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val scheme = if (config.tls) "ssl" else "tcp"
                val serverUri = "$scheme://${config.host}:${config.port}"
                val client = MqttClient(serverUri, "$clientId-probe", MemoryPersistence())
                try {
                    val options = MqttConnectionOptions().apply {
                        isCleanStart = true
                        sessionExpiryInterval = 0
                        isAutomaticReconnect = false
                        keepAliveInterval = KEEP_ALIVE_SEC
                        connectionTimeout = TEST_TIMEOUT_SEC
                        if (config.username.isNotBlank()) {
                            userName = config.username
                            password = config.password.toByteArray()
                        }
                        if (config.tls) {
                            socketFactory =
                                MqttTls.socketFactory(config.caCertPem, config.clientCertPem, config.clientKeyPem)
                        }
                    }
                    client.connect(options)
                } finally {
                    runCatching { if (client.isConnected) client.disconnect() }
                    runCatching { client.close() }
                }
            }
        }

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
