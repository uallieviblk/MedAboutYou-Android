// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.domain.DayAdherence
import com.uallsi.medaboutyou.ui.AppViewModelFactory
import com.uallsi.medaboutyou.ui.theme.MedColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(modifier: Modifier = Modifier) {
    val vm: InsightsViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }

    Box(modifier.fillMaxSize()) {
    if (state.loading) {
        CircularProgressIndicator(Modifier.align(Alignment.Center))
        return@Box
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(stringResource(R.string.rate_7), state.rate7, Modifier.weight(1f))
                StatCard(stringResource(R.string.rate_30), state.rate30, Modifier.weight(1f))
                StatCard(stringResource(R.string.rate_90), state.rate90, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueCard(stringResource(R.string.day_streak_label), state.streak.toString(), Modifier.weight(1f))
                ValueCard(stringResource(R.string.missed_30), state.missed30.toString(), Modifier.weight(1f))
            }
        }

        item { SectionTitle(stringResource(R.string.last_30_days)) }
        item { Heatmap(state.heatmap) }

        if (state.byMedicine.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.adherence_by_medicine)) }
            items(state.byMedicine) { (name, stats) ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text("${(stats.rate * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(
                        progress = { stats.rate.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = rateColor(stats.rate),
                    )
                }
            }
        }

        if (state.shoppingList.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.shopping_list)) }
            items(state.shoppingList, key = { it.medKey }) { entry ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.medName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.removeFromShopping(entry.medKey) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove))
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.next_refill)) }
        if (state.refills.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.well_stocked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.refills) { r ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(r.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.refill_by, "%04d-%02d-%02d".format(r.year, r.month, r.day), pluralStringResource(R.plurals.doses_left, r.dosesLeft, r.dosesLeft)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

private fun rateColor(rate: Double): Color = when {
    rate >= 0.9 -> MedColors.success
    rate >= 0.6 -> MedColors.warning
    else -> MedColors.error
}

@Composable
private fun StatCard(label: String, rate: Double, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = rateColor(rate).copy(alpha = 0.15f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("${(rate * 100).roundToInt()}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ValueCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Heatmap(days: List<DayAdherence>) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(1),
        modifier = Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(days) { d ->
            val color = when {
                d.scheduled == 0 -> MedColors.neutralContainer
                d.taken == d.scheduled -> MedColors.success
                d.taken > 0 -> MedColors.warning
                else -> MedColors.error
            }
            Surface(
                color = color,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(28.dp),
            ) {}
        }
    }
}
