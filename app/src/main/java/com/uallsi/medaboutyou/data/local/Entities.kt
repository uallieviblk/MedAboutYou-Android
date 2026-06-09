package com.uallsi.medaboutyou.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey @ColumnInfo(name = "med_key") val medKey: String,
    @ColumnInfo(name = "doses") val doses: Int,
)

/** A prescription. Append-only in spirit (cancel = active 0). */
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
    val notes: String,
    val active: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

/** Whether a specific occurrence was taken or skipped. */
@Entity(
    tableName = "dose_log",
    indices = [Index(value = ["schedule_id", "scheduled_at"], unique = true)],
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "schedule_id") val scheduleId: Long,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: String,  // key_iso
    val status: String,                                          // taken | untaken
    @ColumnInfo(name = "logged_at") val loggedAt: String,
)

/** A single-occurrence override (retime or cancel one dose). */
@Entity(
    tableName = "occ_override",
    indices = [Index(value = ["schedule_id", "scheduled_at"], unique = true)],
)
data class OccOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "schedule_id") val scheduleId: Long,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: String,  // key_iso (original)
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "window_minutes") val windowMinutes: Int,
    val cancelled: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

/** Cached packaging image (BLOB) or a negative-cache marker. */
@Entity(tableName = "image_cache")
data class ImageCacheEntity(
    @PrimaryKey @ColumnInfo(name = "med_key") val medKey: String,
    val bytes: ByteArray?,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    val status: String,   // "ok" | "unavailable"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageCacheEntity) return false
        return medKey == other.medKey && status == other.status &&
            sourceUrl == other.sourceUrl && (bytes?.contentEquals(other.bytes ?: ByteArray(0)) ?: (other.bytes == null))
    }

    override fun hashCode(): Int = medKey.hashCode()
}
