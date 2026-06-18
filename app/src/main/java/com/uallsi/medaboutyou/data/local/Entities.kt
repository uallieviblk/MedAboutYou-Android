// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Cached medicine record. Mirrors the C++ `medicines` table in
 * `MedicineDatabase`, keyed by `source + ext_id`.
 */
@Entity(
    tableName = "medicines",
    primaryKeys = ["source", "ext_id"],
)
data class MedicineEntity(
    val source: String,
    @ColumnInfo(name = "ext_id") val extId: String,
    val category: String = "",
    val name: String = "",
    @ColumnInfo(name = "product_number") val productNumber: String = "",
    val status: String = "",
    @ColumnInfo(name = "opinion_status") val opinionStatus: String = "",
    val inn: String = "",
    @ColumnInfo(name = "active_substance") val activeSubstance: String = "",
    @ColumnInfo(name = "therapeutic_area") val therapeuticArea: String = "",
    @ColumnInfo(name = "atc_code") val atcCode: String = "",
    @ColumnInfo(name = "pharmacotherapeutic_group") val pharmacotherapeuticGroup: String = "",
    @ColumnInfo(name = "therapeutic_indication") val therapeuticIndication: String = "",
    @ColumnInfo(name = "ma_holder") val marketingAuthorisationHolder: String = "",
    // Legacy column, no longer surfaced in the domain model. Retained (always
    // "") so the re-downloadable medicines cache needs no destructive migration.
    val species: String = "",
    @ColumnInfo(name = "pharmaceutical_form") val pharmaceuticalForm: String = "",
    val route: String = "",
    val prescription: String = "",
    @ColumnInfo(name = "has_rcp") val hasRcp: Boolean = false,
    @ColumnInfo(name = "rcp_url") val rcpUrl: String = "",
    @ColumnInfo(name = "additional_monitoring") val additionalMonitoring: Boolean = false,
    @ColumnInfo(name = "advanced_therapy") val advancedTherapy: Boolean = false,
    val biosimilar: Boolean = false,
    @ColumnInfo(name = "conditional_approval") val conditionalApproval: Boolean = false,
    @ColumnInfo(name = "exceptional_circumstances") val exceptionalCircumstances: Boolean = false,
    val generic: Boolean = false,
    val orphan: Boolean = false,
    val prime: Boolean = false,
    @ColumnInfo(name = "accelerated_assessment") val acceleratedAssessment: Boolean = false,
    @ColumnInfo(name = "ma_date") val marketingAuthorisationDate: String = "",
    @ColumnInfo(name = "ec_decision_date") val ecDecisionDate: String = "",
    @ColumnInfo(name = "revision_number") val revisionNumber: String = "",
    @ColumnInfo(name = "first_published") val firstPublished: String = "",
    @ColumnInfo(name = "last_updated") val lastUpdated: String = "",
    val url: String = "",
)

/** Key/value metadata (e.g. the EMA dataset timestamp). */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/**
 * Stock on hand, keyed `ema:<ext_id>` or `name:<lowercased name>` exactly as
 * the C++ `inventory` table.
 */
@Serializable
@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey @ColumnInfo(name = "med_key") val medKey: String,
    @ColumnInfo(name = "doses") val doses: Int,
)

/** A prescription. Append-only in spirit (cancel = active 0). */
@Serializable
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "med_source") val medSource: String,
    @ColumnInfo(name = "med_ext_id") val medExtId: String,
    @ColumnInfo(name = "med_name") val medName: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_mode") val endMode: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "dose_count") val doseCount: Int,
    @ColumnInfo(name = "period_unit") val periodUnit: String,
    @ColumnInfo(name = "period_n") val periodN: Int,
    // Legacy single-dose time (kept for back-compat / fallback). The full set of
    // dose times lives in [times], serialised; see Mappers.
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "times") val times: String = "",
    @ColumnInfo(name = "window_minutes") val windowMinutes: Int,
    val suspended: Boolean = false,
    @ColumnInfo(name = "suspended_until") val suspendedUntil: String = "",
    @ColumnInfo(name = "caregiver_alert_min") val caregiverAlertMin: Int = 0,
    @ColumnInfo(name = "alert_refresh_min") val alertRefreshMin: Int = 0,
    val notes: String,
    val active: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

/** Whether a specific occurrence was taken or skipped. */
@Serializable
@Entity(
    tableName = "dose_log",
    indices = [Index(value = ["schedule_id", "scheduled_at"], unique = true)],
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "schedule_id") val scheduleId: Long,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: String, // key_iso
    val status: String, // taken | untaken
    @ColumnInfo(name = "logged_at") val loggedAt: String,
)

/** A single-occurrence override (retime or cancel one dose). */
@Serializable
@Entity(
    tableName = "occ_override",
    indices = [Index(value = ["schedule_id", "scheduled_at"], unique = true)],
)
data class OccOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "schedule_id") val scheduleId: Long,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: String, // key_iso (original)
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "window_minutes") val windowMinutes: Int,
    val cancelled: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

/**
 * Tracks the last time an alert of a given [kind] fired for an occurrence, so
 * the worker can pace the repeating local reminder and fire the caregiver SMS
 * once. [kind] is "local" or "caregiver".
 */
@Serializable
@Entity(
    tableName = "dose_alert",
    indices = [Index(value = ["schedule_id", "scheduled_at", "kind"], unique = true)],
)
data class DoseAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "schedule_id") val scheduleId: Long,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: String, // key_iso
    val kind: String,
    @ColumnInfo(name = "sent_at") val sentAt: String,
)

/** A medicine the user added to their refill shopping list (from a refill alert). */
@Serializable
@Entity(tableName = "shopping_item")
data class ShoppingItemEntity(
    @PrimaryKey @ColumnInfo(name = "med_key") val medKey: String,
    @ColumnInfo(name = "med_name") val medName: String,
    @ColumnInfo(name = "added_at") val addedAt: String,
)

/**
 * One pause window of a schedule — append-only history. [endDate] is
 * **exclusive** ("" = still open, i.e. an indefinite pause). Days inside a
 * window stay hidden from the calendar and analytics even after they slip into
 * the past: a vacation pause must not retroactively become missed doses.
 */
@Serializable
@Entity(tableName = "pause_period", indices = [Index(value = ["schedule_id"])])
data class PausePeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "schedule_id") val scheduleId: Long,
    @ColumnInfo(name = "start_date") val startDate: String, // "YYYY-MM-DD", inclusive
    @ColumnInfo(name = "end_date") val endDate: String = "", // exclusive; "" = open
)

/**
 * A caregiver MQTT alert queued for **guaranteed delivery**. The row is written
 * before any network attempt and removed only once the broker confirms the
 * publish, so an alert survives process death, reboot, and long offline periods
 * (the LAN broker is often unreachable). The row's [id] is the alert's numeric
 * id carried in the CBOR payload; [category] is a numeric alert-category code.
 */
@Serializable
@Entity(tableName = "mqtt_outbox")
data class MqttOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val user: String,
    val timestamp: String, // ISO-8601, the alert time
    val category: Int,
    val text: String,
    val qos: Int = 2, // exactly-once
    @ColumnInfo(name = "created_at") val createdAt: String,
)

/**
 * One logged user action — the local activity log. [category] and [actionId] are
 * numeric codes (see `ActionCatalog`), each shown in the UI with a localized
 * label; [text] is the full clear text rendered in the locale active when logged.
 */
@Entity(tableName = "action_log")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: String, // ISO-8601, local
    val category: Int,
    @ColumnInfo(name = "action_id") val actionId: Int,
    val text: String,
)
