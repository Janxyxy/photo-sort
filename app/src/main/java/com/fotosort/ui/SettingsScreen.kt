package com.fotosort.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.fotosort.Prefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var skipKept by remember { mutableStateOf(prefs.skipKept) }
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
        Column(Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text("Skip photos I already kept") },
                supportingContent = { Text("Photos you swiped left are hidden the next time you open the folder") },
                trailingContent = {
                    Switch(
                        checked = skipKept,
                        onCheckedChange = {
                            skipKept = it
                            prefs.skipKept = it
                        },
                    )
                },
                modifier = Modifier.clickable {
                    skipKept = !skipKept
                    prefs.skipKept = skipKept
                },
            )
            ListItem(
                headlineContent = { Text("Clear kept history") },
                supportingContent = { Text("$keptCount photos remembered as kept") },
                modifier = Modifier.clickable(enabled = keptCount > 0) { confirmClear = true },
            )
            HorizontalDivider()
            Text(
                "Swipe left to keep, right to delete, up and down to browse without deciding. " +
                    "Deleted photos go to the system trash, where Android keeps them for 30 days.",
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
