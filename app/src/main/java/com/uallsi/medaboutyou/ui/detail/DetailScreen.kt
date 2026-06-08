package com.uallsi.medaboutyou.ui.detail

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.ui.AppViewModelFactory
import com.uallsi.medaboutyou.ui.common.MedicineBadges

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    medicine: Medicine,
    onBack: () -> Unit,
    onSchedule: (Medicine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: DetailViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(medicine.extId, medicine.source) { vm.load(medicine) }

    var stockDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(medicine.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(medicine.name, style = MaterialTheme.typography.headlineSmall)
                    val sub = medicine.inn.ifEmpty { medicine.activeSubstance }
                    if (sub.isNotEmpty()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MedicineBadges(medicine, Modifier.padding(top = 10.dp))
                }
            }

            // Packaging image
            if (state.imageUrl != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = state.imageUrl,
                        contentDescription = stringResource(R.string.image_desc, medicine.name),
                        modifier = Modifier.fillMaxWidth().height(200.dp).padding(8.dp),
                    )
                }
            }

            // Stock card
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column {
                        Text(stringResource(R.string.stock), style = MaterialTheme.typography.labelMedium)
                        Text(pluralStringResource(R.plurals.doses_count, state.stock, state.stock), style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.supplyStock(30) }) { Text(stringResource(R.string.supply_30)) }
                        Button(onClick = { stockDialog = true }) { Text(stringResource(R.string.set_stock)) }
                    }
                }
            }

            InfoSection(stringResource(R.string.section_indication), medicine.therapeuticIndication)
            ClassificationSection(medicine)
            AuthorisationSection(medicine)
            PosologySection(medicine, context)

            // Actions
            Button(
                onClick = { onSchedule(medicine) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Text("  " + stringResource(R.string.add_to_schedule))
            }
            if (medicine.url.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, medicine.url.toUri()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Text("  " + stringResource(if (medicine.source == com.uallsi.medaboutyou.model.Source.AIFA) R.string.open_aifa else R.string.open_epar))
                }
            }
        }
    }

    if (stockDialog) {
        SetStockDialog(
            initial = state.stock,
            onConfirm = { vm.setStock(it); stockDialog = false },
            onDismiss = { stockDialog = false },
        )
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    if (body.isBlank()) return
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ClassificationSection(med: Medicine) {
    val rows = buildList {
        if (med.activeSubstance.isNotEmpty()) add(stringResource(R.string.label_active_substance) to med.activeSubstance)
        if (med.atcCode.isNotEmpty()) add(stringResource(R.string.label_atc) to med.atcCode)
        if (med.pharmacotherapeuticGroup.isNotEmpty()) add(stringResource(R.string.label_pharmacotherapeutic) to med.pharmacotherapeuticGroup)
        if (med.therapeuticArea.isNotEmpty()) add(stringResource(R.string.label_therapeutic_area) to med.therapeuticArea)
        if (med.pharmaceuticalForm.isNotEmpty()) add(stringResource(R.string.label_pharmaceutical_form) to med.pharmaceuticalForm)
        if (med.route.isNotEmpty()) add(stringResource(R.string.label_route) to med.route)
    }
    LabeledGroup(stringResource(R.string.section_classification), rows)
}

@Composable
private fun AuthorisationSection(med: Medicine) {
    val rows = buildList {
        if (med.marketingAuthorisationHolder.isNotEmpty()) add(stringResource(R.string.label_ma_holder) to med.marketingAuthorisationHolder)
        if (med.productNumber.isNotEmpty()) add(stringResource(R.string.label_product_number) to med.productNumber)
        if (med.marketingAuthorisationDate.isNotEmpty()) add(stringResource(R.string.label_ma_date) to med.marketingAuthorisationDate)
        if (med.lastUpdated.isNotEmpty()) add(stringResource(R.string.label_last_updated) to med.lastUpdated)
        if (med.prescription.isNotEmpty()) add(stringResource(R.string.label_supply) to med.prescription)
    }
    LabeledGroup(stringResource(R.string.section_authorisation), rows)
}

@Composable
private fun LabeledGroup(title: String, rows: List<Pair<String, String>>) {
    if (rows.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.4f),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                }
            }
        }
    }
}

@Composable
private fun PosologySection(med: Medicine, context: android.content.Context) {
    val link = when {
        med.hasRcp && med.rcpUrl.isNotEmpty() -> med.rcpUrl
        med.url.isNotEmpty() -> med.url
        else -> ""
    }
    if (link.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.section_posology), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.posology_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) }) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Text("  " + stringResource(if (med.hasRcp) R.string.read_rcp else R.string.read_product_info))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetStockDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial.toString()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_stock_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text(stringResource(R.string.doses_on_hand)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.toIntOrNull() ?: 0) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
