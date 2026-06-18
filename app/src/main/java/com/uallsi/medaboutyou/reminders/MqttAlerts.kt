// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.data.local.MqttOutboxEntity
import kotlinx.serialization.Serializable

/**
 * The CBOR-encoded caregiver alert payload published to the broker. [id] is the
 * alert's numeric id (the durable outbox row id), [category] a numeric category.
 */
@Serializable
data class CaregiverAlert(
    val id: Long,
    val user: String,
    val timestamp: String,
    val category: Int,
    val text: String,
)

/** Builds and durably enqueues caregiver MQTT alerts (drained by [MqttOutboxWorker]). */
object MqttAlerts {

    /** Numeric alert categories carried as [CaregiverAlert.category]. */
    const val CATEGORY_MEDICATION_OVERDUE = 1

    /**
     * Enqueue an overdue-dose alert for one caregiver. The row is persisted before
     * any network attempt; [MqttOutboxWorker] CBOR-encodes it (with the row id as
     * the alert id) and publishes it with confirmed delivery. Returns the alert id.
     */
    suspend fun enqueueOverdue(
        container: AppContainer,
        baseTopic: String,
        username: String,
        user: String,
        timestamp: String,
        text: String,
    ): Long = container.db.mqttOutboxDao().insert(
        MqttOutboxEntity(
            topic = "${baseTopic.trimEnd('/')}/${sanitizeTopicLevel(username)}",
            user = user,
            timestamp = timestamp,
            category = CATEGORY_MEDICATION_OVERDUE,
            text = text,
            qos = 2,
            createdAt = timestamp,
        ),
    )

    /** Strip MQTT wildcard/separator chars so a username is a single safe topic level. */
    fun sanitizeTopicLevel(raw: String): String =
        raw.trim().replace(Regex("[\\s/#+]"), "_").ifBlank { "unknown" }
}
