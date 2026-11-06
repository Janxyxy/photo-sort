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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.fotosort.Prefs
import com.fotosort.SortOrder
import com.fotosort.ThemeMode
import com.fotosort.app
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var skipKept by remember { mutableStateOf(prefs.skipKept) }
    var includeVideos by remember { mutableStateOf(prefs.includeVideos) }
    var groupSimilar by remember { mutableStateOf(prefs.groupSimilar) }
    var sortOrder by remember { mutableStateOf(prefs.sortOrder) }
    var rightKeeps by remember { mutableStateOf(prefs.swipeRightToKeep) }
    var trashOnLeave by remember { mutableStateOf(prefs.trashOnLeave) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var keptCount by remember { mutableIntStateOf(prefs.keptIds.size) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearAnalysis by remember { mutableStateOf(false) }
    var analyzedCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { analyzedCount = context.app.eyeIndex.snapshot().size }

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
            SectionTitle("Swiping")
            SwitchItem(
                title = "Swipe right to keep",
                summary = if (rightKeeps) "Right keeps, left deletes" else "Left keeps, right deletes",
                checked = rightKeeps,
                onChange = {
                    rightKeeps = it
                    prefs.swipeRightToKeep = it
                },
            )
            SwitchItem(
                title = "Vibrate on swipe",
                summary = "A short buzz when a swipe goes far enough to count",
                checked = haptics,
                onChange = {
                    haptics = it
                    prefs.haptics = it
                },
            )
            SwitchItem(
                title = "Skip photos I already kept",
                summary = "Photos you kept are hidden the next time you open the folder",
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
                RadioItem(order.label, selected = order == sortOrder) {
                    sortOrder = order
                    prefs.sortOrder = order
                }
            }

            HorizontalDivider()
            SectionTitle("Trash")
            SwitchItem(
                title = "Move to trash when leaving a folder",
                summary = if (trashOnLeave) {
                    "Asks to move marked items to the trash as you go back"
                } else {
                    "Marked items wait until you move them from the folder list"
                },
                checked = trashOnLeave,
                onChange = {
                    trashOnLeave = it
                    prefs.trashOnLeave = it
                },
            )
            ListItem(
                headlineContent = { Text("Space freed") },
                supportingContent = { Text("${formatSize(context, prefs.freedBytes)} moved to the trash so far") },
            )

            HorizontalDivider()
            SectionTitle("Appearance")
            ThemeMode.entries.forEach { mode ->
                RadioItem(mode.label, selected = mode == prefs.theme) { prefs.theme = mode }
            }

            HorizontalDivider()
            SectionTitle("Stored data")
            ListItem(
                headlineContent = { Text("Clear kept history") },
                supportingContent = { Text("$keptCount items remembered as kept") },
                modifier = Modifier.clickable(enabled = keptCount > 0) { confirmClear = true },
            )
            ListItem(
                headlineContent = { Text("Clear photo analysis") },
                supportingContent = { Text("$analyzedCount photos analyzed for categories") },
                modifier = Modifier.clickable(enabled = analyzedCount > 0) { confirmClearAnalysis = true },
            )

            HorizontalDivider()
            Text(
                "Swipe ${if (rightKeeps) "right" else "left"} to keep, ${if (rightKeeps) "left" else "right"} to delete, " +
                    "up and down to browse without deciding. Pinch or double-tap to zoom. Deleted items go to " +
                    "the system trash, where Android keeps them for 30 days.",
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

    if (confirmClearAnalysis) {
        AlertDialog(
            onDismissRequest = { confirmClearAnalysis = false },
            title = { Text("Clear photo analysis?") },
            text = { Text("Categories will be empty until you analyze your photos again. The downloaded model stays.") },
            confirmButton = {
                TextButton(onClick = {
                    context.app.eyeScanner.stop()
                    scope.launch { context.app.eyeIndex.clear() }
                    analyzedCount = 0
                    confirmClearAnalysis = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAnalysis = false }) { Text("Cancel") }
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
private fun RadioItem(label: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.clickable(onClick = onSelect),
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
