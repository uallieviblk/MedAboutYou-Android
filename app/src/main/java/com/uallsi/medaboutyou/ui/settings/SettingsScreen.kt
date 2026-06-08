package com.uallsi.medaboutyou.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.BuildConfig
import com.uallsi.medaboutyou.ui.AppViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        ListItem(
            headlineContent = { Text("Enable dose reminders") },
            supportingContent = { Text("Notify me when a scheduled dose is due") },
            trailingContent = {
                Switch(checked = state.remindersEnabled, onCheckedChange = vm::setRemindersEnabled)
            },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Start at boot") },
            supportingContent = { Text("Re-arm reminders automatically after a reboot") },
            trailingContent = {
                Switch(checked = state.startAtBoot, onCheckedChange = vm::setStartAtBoot)
            },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("About MedAboutYou") },
            supportingContent = {
                Text("Version ${BuildConfig.VERSION_NAME} · App ID ${BuildConfig.APPLICATION_ID}")
            },
        )
        Text(
            "Data sources: European Medicines Agency public dataset; Italian AIFA database.\n" +
                "All your schedules, dose log and stock stay on this device — nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
