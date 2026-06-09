// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.model.Source
import com.uallsi.medaboutyou.ui.AppViewModelFactory
import com.uallsi.medaboutyou.ui.common.Badge
import com.uallsi.medaboutyou.ui.common.statusBadgeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenMedicine: (Medicine) -> Unit,
    modifier: Modifier = Modifier,
    onCustomMedicine: (String) -> Unit = {},
    onScan: () -> Unit = {},
    prefillQuery: String? = null,
    onConsumePrefill: () -> Unit = {},
) {
    val vm: SearchViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()

    // A scanned package arrives as an AIFA query.
    androidx.compose.runtime.LaunchedEffect(prefillQuery) {
        if (prefillQuery != null) {
            vm.setSource(Source.AIFA)
            vm.setQuery(prefillQuery)
            onConsumePrefill()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SegmentedButton(
                selected = state.source == Source.EMA,
                onClick = { vm.setSource(Source.EMA) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.source_ema)) }
            SegmentedButton(
                selected = state.source == Source.AIFA,
                onClick = { vm.setSource(Source.AIFA) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.source_aifa)) }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onScan) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan_package))
                }
            },
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.source == Source.EMA) {
                    FilterChip(
                        selected = state.vetIncluded,
                        onClick = { vm.setVetIncluded(!state.vetIncluded) },
                        label = { Text(stringResource(R.string.vet)) },
                    )
                    IconButton(onClick = { vm.refreshEma() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_dataset))
                    }
                }
            }
        }

        // Create a "user medicine" therapy from the typed text (for products not
        // in EMA/AIFA, e.g. supplements). The name stays editable in the dialog.
        if (state.query.isNotBlank()) {
            OutlinedButton(
                onClick = { onCustomMedicine(state.query.trim()) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text(stringResource(R.string.use_as_custom, state.query.trim())) }
        }

        if (state.needsDownload && state.results.isEmpty()) {
            EmptyDownload(loading = state.loading, onDownload = { vm.refreshEma() })
        } else if (state.loading && state.results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
                items(state.results, key = { it.source.key + "|" + it.extId + "|" + it.name }) { med ->
                    MedicineRow(med, onClick = { onOpenMedicine(med) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MedicineRow(med: Medicine, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                med.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = med.inn.ifEmpty { med.activeSubstance }
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (med.status.isNotEmpty()) {
            Badge(med.status, statusBadgeColors(med.status))
        }
    }
}

@Composable
private fun EmptyDownload(loading: Boolean, onDownload: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.dataset_not_downloaded),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.dataset_cached_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            if (loading) CircularProgressIndicator()
            else Button(onClick = onDownload) { Text(stringResource(R.string.download_dataset)) }
        }
    }
}
