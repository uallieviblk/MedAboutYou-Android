package com.uallsi.medaboutyou.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uallsi.medaboutyou.BuildConfig
import com.uallsi.medaboutyou.R
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
