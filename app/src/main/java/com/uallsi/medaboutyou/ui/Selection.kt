// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui

import com.uallsi.medaboutyou.model.Medicine

/**
 * Tiny process-lifetime holder for the medicine the user tapped, so the detail
 * screen can render a live (possibly AIFA, not-yet-cached) record without
 * threading it through navigation arguments.
 */
object Selection {
    @Volatile
    var medicine: Medicine? = null
}
