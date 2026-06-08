package com.uallsi.medaboutyou.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.ui.AppViewModelFactory
import com.uallsi.medaboutyou.ui.theme.MedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    val vm: TodayViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

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
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AdherenceCard(state.takenToday, state.dueToday, state.streak) }
        state.nextRefill?.let { refill ->
            item { RefillBanner(refill.name, refill.year, refill.month, refill.day, refill.dosesLeft) }
        }

        // Time-of-day grouped timeline.
        val groups = state.doses.groupBy { blockOf(it.occ.hour) }
        for (block in listOf("Morning", "Afternoon", "Evening", "Night")) {
            val items = groups[block].orEmpty().sortedWith(compareBy({ it.occ.hour }, { it.occ.minute }))
            if (items.isEmpty()) continue
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(block, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (items.any { it.isPast && it.occ.status.isEmpty() }) {
                        TextButton(onClick = { vm.takeAll(items) }) { Text("Take all") }
                    }
                }
            }
            items(items, key = { it.occ.scheduleId.toString() + it.occ.keyIso }) { dose ->
                DoseRow(dose, onToggle = { taken ->
                    if (taken) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.toggle(dose, taken)
                })
            }
        }

        if (state.doses.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No doses scheduled for today.\nAdd a medicine from Search to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
    }
    }
}

private fun blockOf(hour: Int): String = when (hour) {
    in 5..11 -> "Morning"
    in 12..16 -> "Afternoon"
    in 17..20 -> "Evening"
    else -> "Night"
}

@Composable
private fun AdherenceCard(taken: Int, due: Int, streak: Int) {
    val progress = if (due > 0) taken.toFloat() / due else 0f
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(96.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                )
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(96.dp),
                    color = MedColors.success,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text("$taken/$due", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Today's doses", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (due == 0) "Nothing due yet" else "$taken of $due taken",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = MedColors.warning)
                    Text("  $streak-day streak", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun RefillBanner(name: String, year: Int, month: Int, day: Int, left: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MedColors.shortageContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MedColors.shortage)
            Column(Modifier.padding(start = 12.dp)) {
                Text("Refill soon: $name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Runs out %04d-%02d-%02d · %d left".format(year, month, day, left),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DoseRow(dose: TodayDose, onToggle: (Boolean) -> Unit) {
    val occ = dose.occ
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = occ.status == "taken", enabled = dose.isPast, onCheckedChange = onToggle)
            Column(Modifier.weight(1f)) {
                Text("${occ.timeLabel()} — ${occ.medName}", style = MaterialTheme.typography.titleSmall)
                val status = when {
                    occ.status == "taken" -> "Taken"
                    dose.isPast -> "Due now"
                    else -> "Upcoming"
                }
                Text(
                    "$status · ${dose.stock} in stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dose.stock <= 0) {
                Icon(Icons.Default.Warning, contentDescription = "No stock", tint = MedColors.shortage)
            }
        }
    }
}
