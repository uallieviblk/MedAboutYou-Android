// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uallsi.medaboutyou.AppContainer
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Drains the durable caregiver-alert outbox, publishing each row to the MQTT
 * broker with confirmed delivery and removing it only once delivered. Returns
 * [Result.retry] (exponential backoff, network-constrained) while the broker is
 * unreachable, so an alert can't be lost across reboots / long offline periods.
 */
class MqttOutboxWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        val dao = container.db.mqttOutboxDao()
        // Max-age cap: drop alerts undeliverable for > MAX_AGE_DAYS so a
        // permanently-unreachable broker can't grow the outbox without bound.
        dao.pruneOlderThan(LocalDateTime.now().minusDays(MAX_AGE_DAYS).toString())
        val items = dao.pending()
        if (items.isEmpty()) return Result.success()

        val config = container.settings.mqttConfigFlow.first()
        // Disabled or no broker configured: keep the rows; they flush once a
        // broker host is set and the worker is re-kicked.
        if (!config.enabled || config.host.isBlank()) return Result.success()

        val delivered = runCatching {
            MqttPublisher.publish(applicationContext, config, container.settings.mqttClientId(), items)
        }.getOrDefault(emptySet())

        delivered.forEach { dao.delete(it) }
        return if (delivered.size == items.size) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "mqtt_outbox_drain"
        private const val BACKOFF_SEC = 30L
        private const val MAX_AGE_DAYS = 7L

        /** Ensure a (re)tryable drain runs as soon as the network allows. */
        fun kick(context: Context) {
            val request = OneTimeWorkRequestBuilder<MqttOutboxWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SEC, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
