package com.uallsi.medaboutyou.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uallsi.medaboutyou.R

/**
 * A labelled numeric stepper (−/value/+) — replaces free-text numeric entry for
 * bounded quantities (interval, window, dose count), per current input guidance.
 */
@Composable
fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalIconButton(
                onClick = { onChange((value - step).coerceAtLeast(min)) },
                enabled = value > min,
            ) { Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_decrease)) }
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 4.dp),
            )
            FilledTonalIconButton(
                onClick = { onChange((value + step).coerceAtMost(max)) },
                enabled = value < max,
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase)) }
        }
    }
}
