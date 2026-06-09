package com.uallsi.medaboutyou.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.BuildConfig
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.data.local.Caregiver
import com.uallsi.medaboutyou.ui.AppViewModelFactory

/** One selectable UI language (empty tag = follow the system). */
private data class AppLanguage(val tag: String, val label: String)

private val languages = listOf(
    AppLanguage("", "System default"),
    AppLanguage("en", "English"),
    AppLanguage("it", "Italiano"),
    AppLanguage("fr", "Français"),
    AppLanguage("es", "Español"),
    AppLanguage("de", "Deutsch"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        LanguageRow()
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.enable_reminders)) },
            supportingContent = { Text(stringResource(R.string.enable_reminders_desc)) },
            trailingContent = {
                Switch(checked = state.remindersEnabled, onCheckedChange = vm::setRemindersEnabled)
            },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.start_at_boot)) },
            supportingContent = { Text(stringResource(R.string.start_at_boot_desc)) },
            trailingContent = {
                Switch(checked = state.startAtBoot, onCheckedChange = vm::setStartAtBoot)
            },
        )
        HorizontalDivider()
        CaregiverSection(
            caregivers = state.caregivers,
            onChange = vm::setCaregivers,
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.about)) },
            supportingContent = {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.APPLICATION_ID))
            },
        )
        Text(
            stringResource(R.string.privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Global list of caregivers + the SMS-permission grant needed to alert them. */
@Composable
private fun CaregiverSection(
    caregivers: List<Caregiver>,
    onChange: (List<Caregiver>) -> Unit,
) {
    val context = LocalContext.current
    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        smsGranted = it
    }

    // Local editable copy, re-seeded if the persisted list changes elsewhere.
    var local by remember { mutableStateOf(caregivers) }
    LaunchedEffect(caregivers) { if (caregivers != local) local = caregivers }
    fun update(list: List<Caregiver>) { local = list; onChange(list) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.caregiver_section), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.caregiver_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        local.forEachIndexed { i, cg ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = cg.name, singleLine = true,
                        onValueChange = { update(local.toMutableList().also { l -> l[i] = cg.copy(name = it) }) },
                        label = { Text(stringResource(R.string.caregiver_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cg.phone, singleLine = true,
                        onValueChange = { update(local.toMutableList().also { l -> l[i] = cg.copy(phone = it) }) },
                        label = { Text(stringResource(R.string.caregiver_phone)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = { update(local.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove))
                }
            }
        }
        TextButton(onClick = { update(local + Caregiver("", "")) }) {
            Text(stringResource(R.string.caregiver_add))
        }
        if (local.any { it.phone.isNotBlank() } && !smsGranted) {
            TextButton(onClick = { requestSms.launch(Manifest.permission.SEND_SMS) }) {
                Text(stringResource(R.string.caregiver_grant_sms))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageRow() {
    var expanded by remember { mutableStateOf(false) }
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        .substringBefore(",").ifEmpty { "" }
    val currentLabel = languages.firstOrNull { it.tag == current }?.label ?: languages.first().label

    ListItem(
        modifier = Modifier.clickable { expanded = true },
        headlineContent = { Text(stringResource(R.string.language)) },
        supportingContent = { Text(currentLabel) },
        trailingContent = {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.label) },
                        onClick = {
                            expanded = false
                            AppCompatDelegate.setApplicationLocales(
                                if (lang.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                else LocaleListCompat.forLanguageTags(lang.tag),
                            )
                        },
                    )
                }
            }
        },
    )
}
