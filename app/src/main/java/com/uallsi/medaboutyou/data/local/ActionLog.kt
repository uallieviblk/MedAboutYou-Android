// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * Records each user action to the local activity log. The clear [text] is
 * rendered (already localized) by the caller; [category] and [actionId] are
 * numeric codes from [ActionCatalog], each shown with a localized label in the UI.
 * The log is capped at [Settings.actionLogLimitFlow] entries (configurable,
 * default 200) — older rows are pruned after each insert.
 */
class ActionLog(db: MedDatabase, private val settings: Settings) {
    private val dao = db.actionLogDao()

    suspend fun log(category: Int, actionId: Int, text: String) {
        dao.insert(
            ActionLogEntity(
                timestamp = LocalDateTime.now().toString(),
                category = category,
                actionId = actionId,
                text = text,
            ),
        )
        dao.prune(settings.actionLogLimitFlow.first().coerceAtLeast(1))
    }

    /** Convenience: derive the category from the action code. */
    suspend fun log(actionId: Int, text: String) =
        log(ActionCatalog.categoryOf(actionId), actionId, text)

    fun recent(): Flow<List<ActionLogEntity>> = dao.recent()
}

/**
 * The numeric category + action codes for the activity log. Each code maps to a
 * localized label in the UI (see `ActionLogScreen`). Values are stable — don't
 * renumber existing ones (they're persisted in `action_log`).
 */
object ActionCatalog {
    // Categories
    const val CAT_MEDICATION = 1
    const val CAT_SCHEDULE = 2
    const val CAT_INVENTORY = 3
    const val CAT_DATA = 4
    const val CAT_SETTINGS = 5

    // Actions (the action code is the record's numeric "id")
    const val DOSE_TAKEN = 10
    const val DOSE_UNTAKEN = 11
    const val DOSES_TAKEN_ALL = 12
    const val SCHEDULE_CREATED = 20
    const val SCHEDULE_EDITED = 21
    const val DOSE_EDITED = 22
    const val SCHEDULE_CANCELLED = 24
    const val SCHEDULE_PAUSED = 25
    const val SCHEDULE_RESUMED = 26
    const val STOCK_SET = 30
    const val STOCK_ADDED = 31
    const val SHOPPING_REMOVED = 32
    const val BACKUP_EXPORTED = 40
    const val BACKUP_RESTORED = 41
    const val REMINDERS_TOGGLED = 50
    const val BOOT_TOGGLED = 51
    const val CAREGIVER_ADDED = 52
    const val CAREGIVER_REMOVED = 53

    /** The category a given action belongs to. */
    fun categoryOf(actionId: Int): Int = when (actionId) {
        DOSE_TAKEN, DOSE_UNTAKEN, DOSES_TAKEN_ALL -> CAT_MEDICATION
        SCHEDULE_CREATED, SCHEDULE_EDITED, DOSE_EDITED,
        SCHEDULE_CANCELLED, SCHEDULE_PAUSED, SCHEDULE_RESUMED -> CAT_SCHEDULE
        STOCK_SET, STOCK_ADDED, SHOPPING_REMOVED -> CAT_INVENTORY
        BACKUP_EXPORTED, BACKUP_RESTORED -> CAT_DATA
        else -> CAT_SETTINGS
    }
}
