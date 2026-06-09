// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.BuildConfig
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.data.local.Caregiver
import com.uallsi.medaboutyou.ui.AppViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
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
            userName = state.userName,
            onUserName = vm::setUserName,
            caregivers = state.caregivers,
            onChange = vm::setCaregivers,
        )
        HorizontalDivider()
        BackupSection(vm)
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
    userName: String,
    onUserName: (String) -> Unit,
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
        OutlinedTextField(
            value = userName, onValueChange = onUserName, singleLine = true,
            label = { Text(stringResource(R.string.user_name)) },
            modifier = Modifier.fillMaxWidth(),
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

/** Encrypted, fully local backup & restore (the privacy-safe alternative to cloud sync). */
@Composable
private fun BackupSection(vm: SettingsViewModel) {
    val context = LocalContext.current
    var exportPwd by remember { mutableStateOf("") }
    var showExportPwd by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var showImportPwd by remember { mutableStateOf(false) }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            vm.exportBackup(uri, exportPwd) { r ->
                toast(context, r.fold({ context.getString(R.string.backup_saved) }, { it.message ?: "Backup failed" }))
            }
        }
        exportPwd = ""
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { importUri = uri; showImportPwd = true }
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.backup_section), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.backup_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showExportPwd = true }) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(onClick = { openDoc.launch(arrayOf("application/octet-stream", "*/*")) }) {
                Text(stringResource(R.string.backup_restore))
            }
        }
    }

    if (showExportPwd) {
        PasswordDialog(
            title = stringResource(R.string.backup_set_password),
            confirmLabel = stringResource(R.string.backup_export),
            onConfirm = { pwd -> showExportPwd = false; exportPwd = pwd; createDoc.launch("medaboutyou-backup.mab") },
            onDismiss = { showExportPwd = false },
        )
    }
    if (showImportPwd) {
        PasswordDialog(
            title = stringResource(R.string.backup_enter_password),
            confirmLabel = stringResource(R.string.backup_restore),
            onConfirm = { pwd ->
                showImportPwd = false
                importUri?.let { uri ->
                    vm.importBackup(uri, pwd) { r ->
                        toast(context, r.fold({ context.getString(R.string.backup_restored, it) }, { it.message ?: "Restore failed" }))
                    }
                }
            },
            onDismiss = { showImportPwd = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pwd by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_password_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = pwd.length >= 4, onClick = { onConfirm(pwd) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun toast(context: Context, message: String) =
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
