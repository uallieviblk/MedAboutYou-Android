// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.log

import androidx.lifecycle.ViewModel
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.data.local.ActionLogEntity
import kotlinx.coroutines.flow.Flow

/** Streams the local activity-log records, newest first. */
class ActionLogViewModel(container: AppContainer) : ViewModel() {
    val records: Flow<List<ActionLogEntity>> = container.actionLog.recent()
}
