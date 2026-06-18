// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import com.uallsi.medaboutyou.data.local.ActionCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the activity-log category mapping and the uniqueness of the action codes. */
class ActionCatalogTest {

    @Test
    fun `each action resolves to its declared category`() {
        assertEquals(ActionCatalog.CAT_MEDICATION, ActionCatalog.categoryOf(ActionCatalog.DOSE_TAKEN))
        assertEquals(ActionCatalog.CAT_MEDICATION, ActionCatalog.categoryOf(ActionCatalog.DOSES_TAKEN_ALL))
        assertEquals(ActionCatalog.CAT_SCHEDULE, ActionCatalog.categoryOf(ActionCatalog.SCHEDULE_CREATED))
        assertEquals(ActionCatalog.CAT_SCHEDULE, ActionCatalog.categoryOf(ActionCatalog.SCHEDULE_CANCELLED))
        assertEquals(ActionCatalog.CAT_SCHEDULE, ActionCatalog.categoryOf(ActionCatalog.SCHEDULE_PAUSED))
        assertEquals(ActionCatalog.CAT_INVENTORY, ActionCatalog.categoryOf(ActionCatalog.STOCK_SET))
        assertEquals(ActionCatalog.CAT_INVENTORY, ActionCatalog.categoryOf(ActionCatalog.SHOPPING_REMOVED))
        assertEquals(ActionCatalog.CAT_DATA, ActionCatalog.categoryOf(ActionCatalog.BACKUP_EXPORTED))
        assertEquals(ActionCatalog.CAT_DATA, ActionCatalog.categoryOf(ActionCatalog.BACKUP_RESTORED))
        assertEquals(ActionCatalog.CAT_SETTINGS, ActionCatalog.categoryOf(ActionCatalog.REMINDERS_TOGGLED))
        assertEquals(ActionCatalog.CAT_SETTINGS, ActionCatalog.categoryOf(ActionCatalog.CAREGIVER_ADDED))
    }

    @Test
    fun `action codes are all distinct`() {
        val codes = listOf(
            ActionCatalog.DOSE_TAKEN, ActionCatalog.DOSE_UNTAKEN, ActionCatalog.DOSES_TAKEN_ALL,
            ActionCatalog.SCHEDULE_CREATED, ActionCatalog.SCHEDULE_EDITED, ActionCatalog.DOSE_EDITED,
            ActionCatalog.SCHEDULE_CANCELLED, ActionCatalog.SCHEDULE_PAUSED, ActionCatalog.SCHEDULE_RESUMED,
            ActionCatalog.STOCK_SET, ActionCatalog.STOCK_ADDED, ActionCatalog.SHOPPING_REMOVED,
            ActionCatalog.BACKUP_EXPORTED, ActionCatalog.BACKUP_RESTORED,
            ActionCatalog.REMINDERS_TOGGLED, ActionCatalog.BOOT_TOGGLED,
            ActionCatalog.CAREGIVER_ADDED, ActionCatalog.CAREGIVER_REMOVED,
        )
        assertEquals(codes.size, codes.toSet().size)
    }
}
