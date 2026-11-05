package com.fotosort.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fotosort.Folder
import com.fotosort.Media
import com.fotosort.MediaRepository
import com.fotosort.Prefs
import com.fotosort.app
import com.fotosort.eye.CategorySection
import com.fotosort.eye.ScanState
import com.fotosort.eye.buildSections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Photos grouped by what Eye sees in them; each category opens in the swipe screen like a folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    prefs: Prefs,
    onOpen: (Folder) -> Unit,
    onSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scanner = context.app.eyeScanner
    val index = context.app.eyeIndex
    val state by scanner.state.collectAsState()
    val version by index.version.collectAsState()

    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
    val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    var photos by remember { mutableStateOf<List<Media>?>(null) }
    var sections by remember { mutableStateOf<List<CategorySection>>(emptyList()) }
    var analyzedCount by remember { mutableStateOf(0) }
    var listLayout by remember { mutableStateOf(prefs.listLayout) }
    LaunchedEffect(granted) {
        if (granted) photos = MediaRepository.loadAllImages(context)
    }
    LaunchedEffect(photos, version) {
        val library = photos ?: return@LaunchedEffect
        val tags = index.snapshot()
        analyzedCount = library.count { it.id in tags }
        sections = withContext(Dispatchers.Default) { buildSections(library, tags) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
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
        bottomBar = bottomBar,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val library = photos
            when {
                !granted -> Text(
                    "Allow photo access in the Folders tab first.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                )
                library == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    columns = folderColumns(listLayout),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = folderSpacing(listLayout),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ScanCard(
                            state = state,
                            analyzed = analyzedCount,
                            total = library.size,
                            onStart = scanner::start,
                            onStop = scanner::stop,
                        )
                    }
                    val keptIds = if (prefs.skipKept) prefs.keptIds else emptySet()
                    for (section in sections) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header-${section.title}") {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                            )
                        }
                        items(section.folders, key = { it.id }) { folder ->
                            FolderItem(folder, keptIds, listLayout, onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanCard(state: ScanState, analyzed: Int, total: Int, onStart: () -> Unit, onStop: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            when (state) {
                ScanState.Downloading -> {
                    Text("Downloading the photo model (66 MB)…", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onStop) { Text("Cancel") }
                }
                is ScanState.Scanning -> {
                    Text("Analyzing ${state.done} of ${state.total} photos", style = MaterialTheme.typography.titleSmall)
                    state.msPerPhoto?.let { ms ->
                        val minutes = (state.total - state.done) * ms / 60_000
                        Text(
                            if (minutes < 1) "Less than a minute left" else "About $minutes min left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.done.toFloat() / state.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "You can keep using the app. Categories fill in as it goes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onStop) { Text("Pause") }
                    }
                }
                is ScanState.Failed -> {
                    Text(state.message, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onStart) { Text("Try again") }
                }
                ScanState.Idle -> when {
                    analyzed == 0 -> {
                        Text("Sort photos by what's in them", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Finds screenshots, receipts, blurry shots, closed eyes and more. Photos are analyzed " +
                                "on this phone and never uploaded. The first run downloads a 66 MB model.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onStart) { Text("Analyze $total photos") }
                    }
                    analyzed < total -> {
                        Text("$analyzed of $total photos analyzed", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onStart) { Text("Analyze ${total - analyzed} more") }
                    }
                    else -> Text(
                        "All $total photos analyzed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
