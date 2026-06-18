// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.common

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.ui.theme.MedColors

/** Foreground/background pair for a coloured badge. */
data class BadgeColors(val container: Color, val content: Color)

/** Map an authorisation status to its colour, mirroring the desktop rules. */
fun statusBadgeColors(status: String): BadgeColors {
    val s = status.lowercase()
    return when {
        s.contains("authorised") || s.contains("authorized") ->
            BadgeColors(MedColors.successContainer, MedColors.success)
        s.contains("withdrawn") || s.contains("refused") || s.contains("suspended") ||
            s.contains("revoked") || s.contains("expired") ->
            BadgeColors(MedColors.errorContainer, MedColors.error)
        else -> BadgeColors(MedColors.warningContainer, MedColors.warning)
    }
}

@Composable
fun Badge(
    text: String,
    colors: BadgeColors,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The medicine-type indicators that apply, as string-resource ids. */
fun Medicine.typeBadges(): List<Int> = buildList {
    if (generic) add(R.string.badge_generic)
    if (orphan) add(R.string.badge_orphan)
    if (biosimilar) add(R.string.badge_biosimilar)
    if (prime) add(R.string.badge_prime)
    if (advancedTherapy) add(R.string.badge_advanced_therapy)
    if (acceleratedAssessment) add(R.string.badge_accelerated)
    if (conditionalApproval) add(R.string.badge_conditional)
    if (exceptionalCircumstances) add(R.string.badge_exceptional)
    if (additionalMonitoring) add(R.string.badge_additional_monitoring)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicineBadges(medicine: Medicine, modifier: Modifier = Modifier) {
    val accent = BadgeColors(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
    )
    FlowRow(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        if (medicine.status.isNotEmpty()) {
            Badge(medicine.status, statusBadgeColors(medicine.status))
        }
        medicine.typeBadges().forEach { Badge(stringResource(it), accent) }
    }
}
