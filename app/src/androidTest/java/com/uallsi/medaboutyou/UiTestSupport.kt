// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.uallsi.medaboutyou.model.Source
import kotlinx.coroutines.runBlocking

/**
 * Shared helpers that keep the Compose UI instrumented tests deterministic on
 * slow, software-rendered emulators and — crucially — under full-suite
 * contention, where the persistent Room database is shared across tests.
 *
 * Two robustness rules these enforce:
 *  - **Never assert before the screen is ready.** `waitForIdle()` does not wait
 *    for a ViewModel's `Dispatchers.IO` load, so always poll with [awaitText] et
 *    al. instead of asserting straight after a launch/navigation.
 *  - **Isolate state.** Each test that seeds data must [clearUserData] in
 *    `@Before` so it is independent of run order (a leftover schedule from an
 *    earlier test would otherwise break a later "exactly these" assertion).
 */

/**
 * Generous wait budget. The default Compose `waitUntil` timeout (1 s) and the
 * old hand-coded 10 s are both too tight for a cold MainActivity launch +
 * async DB load + navigation on a swiftshader emulator running the whole suite.
 */
const val UI_TIMEOUT_MS = 30_000L

/** Pre-grant POST_NOTIFICATIONS so the runtime dialog can't cover the activity. */
fun grantNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.grantRuntimePermission(
            inst.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}

/**
 * Wipe all user data and reset the search-affecting preferences, so a test is
 * independent of whatever ran before it on the same (persistent) database.
 * The medicines catalogue is intentionally left intact (it is re-downloadable
 * and tests that need a record seed their own).
 */
fun MedApp.clearUserData() = runBlocking {
    val dao = container.db.backupDao()
    dao.clearDoseAlerts()
    dao.clearOverrides()
    dao.clearDoseLogs()
    dao.clearInventory()
    dao.clearSchedules()
    container.shopping.all().forEach { container.shopping.remove(it.medKey) }
    container.medicines.setMeta("refill_scan_date", "") // reset the daily refill-scan guard
    // Restore the defaults Search relies on (a prior test or manual run may have
    // flipped the source to AIFA or enabled the vet filter).
    container.settings.setSource(Source.EMA)
    container.settings.setVetIncluded(false)
}

/** Wait until at least one node with [text] exists (then return). */
fun ComposeTestRule.awaitText(text: String, substring: Boolean = false, timeoutMs: Long = UI_TIMEOUT_MS) {
    waitUntil(timeoutMs) {
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Wait until at least one node tagged [tag] exists. */
fun ComposeTestRule.awaitTag(tag: String, timeoutMs: Long = UI_TIMEOUT_MS) {
    waitUntil(timeoutMs) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Wait until at least one node with [contentDescription] exists. */
fun ComposeTestRule.awaitContentDescription(contentDescription: String, timeoutMs: Long = UI_TIMEOUT_MS) {
    waitUntil(timeoutMs) {
        onAllNodesWithContentDescription(contentDescription).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Wait until no node with [contentDescription] remains. */
fun ComposeTestRule.awaitContentDescriptionGone(contentDescription: String, timeoutMs: Long = UI_TIMEOUT_MS) {
    waitUntil(timeoutMs) {
        onAllNodesWithContentDescription(contentDescription).fetchSemanticsNodes().isEmpty()
    }
}
