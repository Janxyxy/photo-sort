package com.fotosort.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fotosort.Media

/** Photos taken at most this far apart (chained) count as one burst. */
private const val SIMILAR_GAP_MS = 3_000L

/**
 * Groups photos shot in quick succession. Only photos with a real capture date are considered,
 * because copied files often share the same "date added".
 * Returns a map from each grouped photo's id to its whole group (oldest first).
 */
fun similarGroups(items: List<Media>): Map<Long, List<Media>> {
    val photos = items.filter { !it.isVideo && it.takenMs > 0 }.sortedBy { it.takenMs }
    val result = HashMap<Long, List<Media>>()
    var run = mutableListOf<Media>()

    fun flush() {
        if (run.size > 1) {
            val group = run.toList()
            group.forEach { result[it.id] = group }
        }
        run = mutableListOf()
    }

    for (photo in photos) {
        if (run.isNotEmpty() && photo.takenMs - run.last().takenMs > SIMILAR_GAP_MS) flush()
        run += photo
    }
    flush()
    return result
}

/**
 * Lets the user pick which shots of a burst to keep; the rest get marked for trash.
 * The largest file is preselected, since for the same scene it is usually the sharpest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarSheet(group: List<Media>, onDismiss: () -> Unit, onApply: (keepIds: Set<Long>) -> Unit) {
    val best = remember(group) { group.maxBy { it.size }.id }
    var keep by remember(group) { mutableStateOf(setOf(best)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text("${group.size} similar photos", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tap the ones to keep. The largest file is picked by default; it's usually the sharpest.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 420.dp).padding(vertical = 12.dp),
            ) {
                items(group, key = { it.id }) { media ->
                    val kept = media.id in keep
                    SelectableThumbnail(
                        media = media,
                        keep = kept,
                        label = if (media.id == best) "Best" else null,
                        onClick = { keep = if (kept) keep - media.id else keep + media.id },
                    )
                }
            }
            Button(onClick = { onApply(keep) }, modifier = Modifier.fillMaxWidth()) {
                Text("Keep ${keep.size}, delete ${group.size - keep.size}")
            }
        }
    }
}
