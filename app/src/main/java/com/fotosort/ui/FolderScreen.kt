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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/** The first entry decides whether the app can work; videos are optional. */
private val mediaPermissions =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    prefs: Prefs,
    onOpen: (Folder) -> Unit,
    onSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    fun hasAccess() = context.checkSelfPermission(mediaPermissions[0]) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasAccess()) }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAccess()
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(mediaPermissions)
    }
    // picks up a permission granted from the system settings screen
    LifecycleResumeEffect(Unit) {
        granted = hasAccess()
        onPauseOrDispose {}
    }

    var folders by remember { mutableStateOf<List<Folder>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var pendingCount by remember { mutableIntStateOf(prefs.pendingIds.size) }
    var showReview by remember { mutableStateOf(false) }
    var listLayout by remember { mutableStateOf(prefs.listLayout) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(granted, reload) {
        if (granted) folders = MediaRepository.loadFolders(context, prefs.includeVideos)
    }

    fun handleTrashed(bytes: Long) {
        pendingCount = prefs.pendingIds.size
        reload++
        if (bytes > 0) scope.launch { snackbar.showSnackbar("Freed ${formatSize(context, bytes)}") }
    }
    val trash = rememberTrashLauncher(prefs) { _, bytes -> handleTrashed(bytes) }

    if (showReview) {
        ReviewScreen(
            prefs = prefs,
            onClose = {
                showReview = false
                pendingCount = prefs.pendingIds.size
                reload++
            },
            onTrashed = { _, bytes ->
                showReview = false
                handleTrashed(bytes)
            },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text("FotoSort") },
                actions = {
                    LayoutToggleButton(listLayout) {
                        listLayout = !listLayout
                        prefs.listLayout = listLayout
                    }
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
                            permissionLauncher.launch(mediaPermissions)
                        }
                    },
                )
                folders == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                folders!!.isEmpty() -> Text("No photos found", Modifier.align(Alignment.Center))
                else -> FolderGrid(
                    folders = folders!!,
                    keptIds = if (prefs.skipKept) prefs.keptIds else emptySet(),
                    pendingCount = pendingCount,
                    listLayout = listLayout,
                    onReview = { showReview = true },
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
            "FotoSort needs access to your photos and videos to show your folders.",
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
    listLayout: Boolean,
    onReview: () -> Unit,
    onTrash: () -> Unit,
    onOpen: (Folder) -> Unit,
) {
    LazyVerticalGrid(
        columns = folderColumns(listLayout),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = folderSpacing(listLayout),
    ) {
        if (pendingCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$pendingCount marked for trash", Modifier.weight(1f))
                        TextButton(onClick = onReview) { Text("Review") }
                        FilledTonalButton(onClick = onTrash) { Text("Move to trash") }
                    }
                }
            }
        }
        items(folders, key = { it.id }) { folder ->
            // counting a big folder's kept items on every scroll bind adds up
            val subtitle = remember(folder, keptIds) { folderCountText(folder, keptIds) }
            FolderItem(folder, subtitle, listLayout, onOpen)
        }
    }
}

/** Cover, name and a line of detail such as "N items · M left"; also used for the categories. */
@Composable
fun FolderCard(folder: Folder, subtitle: String, onOpen: (Folder) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen(folder) }) {
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
        Text(
            subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}
