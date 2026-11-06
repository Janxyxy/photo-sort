package com.photosort.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.photosort.Media

/**
 * Decide on many items at once instead of swiping each: selected ones get marked for trash, the
 * rest are kept. Nothing is deleted here; the usual review and system trash dialog still follow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkSelectScreen(
    items: List<Media>,
    preselectAll: Boolean,
    onClose: () -> Unit,
    onApply: (trashIds: Set<Long>) -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val allIds = remember(items) { items.mapTo(HashSet()) { it.id } }
    var selected by remember(items) { mutableStateOf(if (preselectAll) allIds.toSet() else emptySet()) }
    val selectedBytes = items.filter { it.id in selected }.sumOf { it.size }
    val keepCount = items.size - selected.size

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                },
                title = {
                    Column {
                        Text("Select to delete")
                        Text(
                            "${formatCount(selected.size)} of ${formatCount(items.size)} · ${formatSize(context, selectedBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    val all = selected.size == items.size
                    TextButton(onClick = { selected = if (all) emptySet() else allIds.toSet() }) {
                        Text(if (all) "Select none" else "Select all")
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { onApply(selected) },
                colors = if (selected.isEmpty()) ButtonDefaults.buttonColors()
                else ButtonDefaults.buttonColors(containerColor = TrashColor, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) {
                Text(
                    when {
                        selected.isEmpty() -> "Keep all ${formatCount(keepCount)}"
                        keepCount == 0 -> "Mark ${formatCount(selected.size)} for trash"
                        else -> "Mark ${formatCount(selected.size)} for trash, keep ${formatCount(keepCount)}"
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                "Selected items are marked for trash, the rest are kept. You can still review before anything is deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items, key = { it.id }) { media ->
                    val trash = media.id in selected
                    SelectableThumbnail(
                        media = media,
                        keep = !trash,
                        onClick = { selected = if (trash) selected - media.id else selected + media.id },
                    )
                }
            }
        }
    }
}
