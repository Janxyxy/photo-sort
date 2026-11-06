package com.fotosort.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fotosort.Folder

/** Columns for the folder/category lists: tiles in grid mode, one full-width row each in list mode. */
fun folderColumns(listLayout: Boolean): GridCells =
    if (listLayout) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 160.dp)

fun folderSpacing(listLayout: Boolean) = Arrangement.spacedBy(if (listLayout) 4.dp else 12.dp)

/** Top bar button that switches between grid and list; shows the layout it switches to. */
@Composable
fun LayoutToggleButton(listLayout: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        if (listLayout) {
            Icon(GridIcon, contentDescription = "Show as grid")
        } else {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show as list")
        }
    }
}

/** A folder as a tile or a row, depending on the layout. */
@Composable
fun FolderItem(folder: Folder, subtitle: String, listLayout: Boolean, onOpen: (Folder) -> Unit) {
    if (listLayout) FolderRow(folder, subtitle, onOpen) else FolderCard(folder, subtitle, onOpen)
}

@Composable
fun FolderRow(folder: Folder, subtitle: String, onOpen: (Folder) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(folder) }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = folder.coverUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                folder.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "N items · M left", the second half only when some are already kept. */
fun folderCountText(folder: Folder, keptIds: Set<Long>): String {
    val total = folder.mediaIds.size
    val left = folder.mediaIds.count { it !in keptIds }
    return (if (total == 1) "1 item" else "$total items") + if (left == total) "" else " · $left left"
}

/** Four squares; icons-core has no grid icon. */
val GridIcon: ImageVector by lazy {
    ImageVector.Builder("Grid", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            for ((x, y) in listOf(3f to 3f, 13f to 3f, 3f to 13f, 13f to 13f)) {
                moveTo(x, y)
                horizontalLineToRelative(8f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(-8f)
                close()
            }
        }
    }.build()
}
