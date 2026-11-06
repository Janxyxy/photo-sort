package com.photosort.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photosort.Media
import com.photosort.MediaRepository
import com.photosort.Prefs

/**
 * Grid of everything marked for trash. Tapping an item rescues it (it becomes kept);
 * the rest can then be moved to the system trash in one go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(prefs: Prefs, onClose: () -> Unit, onTrashed: (trashed: Set<Long>, bytes: Long) -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<Media>?>(null) }
    var rescued by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(Unit) {
        items = MediaRepository.loadByIds(context, prefs.pendingIds)
    }

    fun applyRescued() = rescued.forEach(prefs::markKept)

    val trash = rememberTrashLauncher(prefs) { trashed, bytes ->
        if (trashed.isNotEmpty()) onTrashed(trashed, bytes)
    }

    fun close() {
        applyRescued()
        onClose()
    }
    BackHandler { close() }

    val toTrash = items.orEmpty().filter { it.id !in rescued }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::close) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                },
                title = {
                    Column {
                        Text("Marked for trash")
                        Text(
                            "${toTrash.size} items · ${formatSize(context, toTrash.sumOf { it.size })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    applyRescued()
                    trash()
                },
                enabled = toTrash.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = TrashColor, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("Move ${toTrash.size} to trash", Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val loaded = items
            when {
                loaded == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                loaded.isEmpty() -> Text("Nothing is marked for trash", Modifier.align(Alignment.Center))
                else -> Column {
                    Text(
                        "Tap an item to keep it instead",
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
                        items(loaded, key = { it.id }) { media ->
                            val keep = media.id in rescued
                            SelectableThumbnail(
                                media = media,
                                keep = keep,
                                onClick = { rescued = if (keep) rescued - media.id else rescued + media.id },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Square thumbnail with a keep (green check) or trash (red bin) badge. */
@Composable
fun SelectableThumbnail(media: Media, keep: Boolean, onClick: () -> Unit, label: String? = null) {
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = media.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (keep) 1f else 0.5f,
            modifier = Modifier.fillMaxSize(),
        )
        if (media.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
        Icon(
            if (keep) Icons.Filled.Check else Icons.Filled.Delete,
            contentDescription = if (keep) "Keep" else "Trash",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .background(if (keep) KeepColor else TrashColor, CircleShape)
                .padding(4.dp)
                .size(16.dp),
        )
        if (label != null) {
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
