package com.fotosort.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import com.fotosort.Folder
import com.fotosort.MediaRepository
import com.fotosort.Prefs

private val imagePermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(prefs: Prefs, onOpen: (Folder) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(imagePermission) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(imagePermission)
    }
    // picks up a permission granted from the system settings screen
    LifecycleResumeEffect(Unit) {
        granted = context.checkSelfPermission(imagePermission) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose {}
    }

    var folders by remember { mutableStateOf<List<Folder>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var pendingCount by remember { mutableIntStateOf(prefs.pendingIds.size) }
    LaunchedEffect(granted, reload) {
        if (granted) folders = MediaRepository.loadFolders(context)
    }
    val trash = rememberTrashLauncher(prefs) {
        pendingCount = prefs.pendingIds.size
        reload++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FotoSort") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !granted -> PermissionPrompt(
                    onGrant = {
                        if (askedOnce) {
                            // the system won't show the dialog again after a denial, open app settings instead
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", context.packageName, null)),
                            )
                        } else {
                            permissionLauncher.launch(imagePermission)
                        }
                    },
                )
                folders == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                folders!!.isEmpty() -> Text("No photos found", Modifier.align(Alignment.Center))
                else -> FolderGrid(
                    folders = folders!!,
                    keptIds = if (prefs.skipKept) prefs.keptIds else emptySet(),
                    pendingCount = pendingCount,
                    onTrash = trash,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "FotoSort needs access to your photos to show your folders.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text("Allow access") }
    }
}

@Composable
private fun FolderGrid(
    folders: List<Folder>,
    keptIds: Set<Long>,
    pendingCount: Int,
    onTrash: () -> Unit,
    onOpen: (Folder) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (pendingCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$pendingCount marked for trash", Modifier.weight(1f))
                        FilledTonalButton(onClick = onTrash) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text("Move to trash", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        items(folders, key = { it.id }) { folder ->
            val left = folder.photoIds.count { it !in keptIds }
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen(folder) }) {
                AsyncImage(
                    model = folder.coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                )
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
                )
                val total = folder.photoIds.size
                Text(
                    if (left == total) "$total photos" else "$total photos · $left left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                )
            }
        }
    }
}
