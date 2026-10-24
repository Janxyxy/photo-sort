package com.fotosort.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fotosort.Prefs
import com.fotosort.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var skipKept by remember { mutableStateOf(prefs.skipKept) }
    var includeVideos by remember { mutableStateOf(prefs.includeVideos) }
    var groupSimilar by remember { mutableStateOf(prefs.groupSimilar) }
    var sortOrder by remember { mutableStateOf(prefs.sortOrder) }
    var keptCount by remember { mutableIntStateOf(prefs.keptIds.size) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SwitchItem(
                title = "Skip photos I already kept",
                summary = "Photos you swiped left are hidden the next time you open the folder",
                checked = skipKept,
                onChange = {
                    skipKept = it
                    prefs.skipKept = it
                },
            )
            SwitchItem(
                title = "Include videos",
                summary = "Sort videos together with photos",
                checked = includeVideos,
                onChange = {
                    includeVideos = it
                    prefs.includeVideos = it
                },
            )
            SwitchItem(
                title = "Group similar photos",
                summary = "Photos taken a few seconds apart can be compared and cleaned up together",
                checked = groupSimilar,
                onChange = {
                    groupSimilar = it
                    prefs.groupSimilar = it
                },
            )
            HorizontalDivider()
            SectionTitle("Order")
            SortOrder.entries.forEach { order ->
                ListItem(
                    headlineContent = { Text(order.label) },
                    leadingContent = { RadioButton(selected = order == sortOrder, onClick = null) },
                    modifier = Modifier.clickable {
                        sortOrder = order
                        prefs.sortOrder = order
                    },
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Space freed") },
                supportingContent = { Text("${formatSize(context, prefs.freedBytes)} moved to the trash so far") },
            )
            ListItem(
                headlineContent = { Text("Clear kept history") },
                supportingContent = { Text("$keptCount items remembered as kept") },
                modifier = Modifier.clickable(enabled = keptCount > 0) { confirmClear = true },
            )
            HorizontalDivider()
            Text(
                "Swipe left to keep, right to delete, up and down to browse without deciding. " +
                    "Pinch or double-tap to zoom. Deleted items go to the system trash, where Android " +
                    "keeps them for 30 days.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear kept history?") },
            text = { Text("All photos will show up for sorting again.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.clearKept()
                    keptCount = 0
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SwitchItem(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
