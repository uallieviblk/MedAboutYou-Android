package com.uallsi.medaboutyou.ui.common

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

/** The medicine-type indicators that apply, as labels (desktop badge set). */
fun Medicine.typeBadges(): List<String> = buildList {
    if (generic) add("Generic")
    if (orphan) add("Orphan")
    if (biosimilar) add("Biosimilar")
    if (prime) add("PRIME")
    if (advancedTherapy) add("Advanced therapy")
    if (acceleratedAssessment) add("Accelerated assessment")
    if (conditionalApproval) add("Conditional approval")
    if (exceptionalCircumstances) add("Exceptional circumstances")
    if (additionalMonitoring) add("Additional monitoring")
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
        medicine.typeBadges().forEach { Badge(it, accent) }
    }
}
