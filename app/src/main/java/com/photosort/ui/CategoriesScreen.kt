package com.photosort.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.photosort.Folder
import com.photosort.Media
import com.photosort.MediaRepository
import com.photosort.Prefs
import com.photosort.app
import com.photosort.eye.CategorySection
import com.photosort.eye.SECTION_CLEAN_UP
import com.photosort.eye.ScanState
import com.photosort.eye.buildSections
import com.photosort.eye.popularTags
import com.photosort.eye.searchTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lives in the app shell so the open section and search survive opening a category and coming back. */
class CategoriesState {
    var query by mutableStateOf("")
    var section by mutableStateOf<String?>(null)
}

private const val SECTION_DONE = "Done"

/** Rows shown per section in list layout before "See all". */
private const val LIST_PREVIEW = 4

/** New photos up to this many are analyzed automatically once the user has run a scan. */
private const val AUTO_SCAN_MAX = 300

/** A category plus how much of it is still undecided (neither kept nor marked for trash). */
private data class Tile(val folder: Folder, val left: Int, val leftBytes: Long)

private data class TileSection(val title: String, val tiles: List<Tile>)

/** Photos grouped by what Eye sees in them; each category opens in the swipe screen like a folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    prefs: Prefs,
    ui: CategoriesState,
    onOpen: (Folder) -> Unit,
    onSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scanner = context.app.eyeScanner
    val index = context.app.eyeIndex
    val state by scanner.state.collectAsState()
    // the scan runs either way; without the permission there is just no progress notification
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scanner.start()
    }
    val startScan = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scanner.start()
        }
    }
    val version by index.version.collectAsState()

    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
    val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    var photos by remember { mutableStateOf<List<Media>?>(null) }
    var sections by remember { mutableStateOf<List<CategorySection>>(emptyList()) }
    var popular by remember { mutableStateOf<List<String>>(emptyList()) }
    var results by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var analyzedCount by remember { mutableIntStateOf(0) }
    var listLayout by remember { mutableStateOf(prefs.listLayout) }
    var searchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(granted) {
        if (granted) photos = MediaRepository.loadAllImages(context)
    }
    LaunchedEffect(photos, version) {
        val library = photos ?: return@LaunchedEffect
        val tags = index.snapshot()
        analyzedCount = library.count { it.id in tags }
        withContext(Dispatchers.Default) {
            sections = buildSections(library, tags, prefs.hiddenCategories)
            popular = popularTags(library, tags, prefs.hiddenCategories)
        }
    }
    LaunchedEffect(photos, version, ui.query) {
        val library = photos ?: return@LaunchedEffect
        if (ui.query.isBlank()) return@LaunchedEffect
        val tags = index.snapshot()
        results = withContext(Dispatchers.Default) { searchTags(library, tags, ui.query, prefs.hiddenCategories) }
    }
    // photos taken since the last scan get analyzed in the background once the user has opted in
    LaunchedEffect(photos, analyzedCount) {
        val total = photos?.size ?: return@LaunchedEffect
        if (analyzedCount > 0 && total - analyzedCount in 1..AUTO_SCAN_MAX) scanner.autoStart()
    }

    // read once per visit; returning from a category recreates this screen
    val decided = remember { prefs.keptIds + prefs.pendingIds }
    var sizeById by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var tileSections by remember { mutableStateOf<List<TileSection>>(emptyList()) }
    var resultTiles by remember { mutableStateOf<List<Tile>>(emptyList()) }
    // off the main thread: these walk every photo id of every category
    LaunchedEffect(photos) {
        val library = photos ?: return@LaunchedEffect
        sizeById = withContext(Dispatchers.Default) { library.associate { it.id to it.size } }
    }
    LaunchedEffect(sections, sizeById) {
        tileSections = withContext(Dispatchers.Default) { buildTileSections(sections, decided, sizeById) }
    }
    LaunchedEffect(results, sizeById) {
        resultTiles = withContext(Dispatchers.Default) { results.map { tileFor(it, decided, sizeById) } }
    }
    val openSection = tileSections.firstOrNull { it.title == ui.section }

    BackHandler(enabled = ui.section != null) { ui.section = null }
    BackHandler(enabled = ui.section == null && (ui.query.isNotEmpty() || searchFocused)) {
        ui.query = ""
        focus.clearFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (ui.section != null) {
                        IconButton(onClick = { ui.section = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = { Text(ui.section ?: "Categories") },
                actions = {
                    LayoutToggleButton(listLayout) {
                        listLayout = !listLayout
                        prefs.listLayout = listLayout
                    }
                    if (ui.section == null) {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
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
                ui.section != null -> TileGrid(
                    tiles = openSection?.tiles.orEmpty(),
                    listLayout = listLayout,
                    emptyText = "Nothing here anymore",
                    onOpen = onOpen,
                )
                else -> Column(Modifier.fillMaxSize()) {
                    if (analyzedCount > 0) {
                        TagSearchField(
                            query = ui.query,
                            onChange = { ui.query = it },
                            onFocusChange = { searchFocused = it },
                        )
                    }
                    when {
                        ui.query.isNotBlank() -> TileGrid(
                            tiles = resultTiles,
                            listLayout = listLayout,
                            emptyText = "No photos tagged “${ui.query.trim()}”",
                            onOpen = onOpen,
                        )
                        searchFocused -> PopularTags(popular, onPick = { ui.query = it })
                        else -> Overview(
                            sections = tileSections,
                            listLayout = listLayout,
                            scanCard = {
                                // hidden once everything is analyzed; new photos bring it back
                                if (state !is ScanState.Idle || analyzedCount < library.size) {
                                    ScanCard(
                                        state = state,
                                        analyzed = analyzedCount,
                                        total = library.size,
                                        onStart = startScan,
                                        onStop = scanner::stop,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    )
                                }
                            },
                            showEmpty = analyzedCount > 0 && state is ScanState.Idle,
                            onOpen = onOpen,
                            onSeeAll = { ui.section = it },
                        )
                    }
                }
            }
        }
    }
}

private fun tileFor(folder: Folder, decided: Set<Long>, sizeById: Map<Long, Long>): Tile {
    var left = 0
    var bytes = 0L
    for (id in folder.mediaIds) {
        if (id in decided) continue
        left++
        bytes += sizeById[id] ?: 0L
    }
    return Tile(folder, left, bytes)
}

/** Open categories per section (Clean up biggest first), then everything finished in one Done section. */
private fun buildTileSections(
    sections: List<CategorySection>,
    decided: Set<Long>,
    sizeById: Map<Long, Long>,
): List<TileSection> {
    val all = sections.map { section -> section.title to section.folders.map { tileFor(it, decided, sizeById) } }
    val active = all.map { (title, tiles) ->
        val open = tiles.filter { it.left > 0 }
        TileSection(title, if (title == SECTION_CLEAN_UP) open.sortedByDescending { it.leftBytes } else open)
    }
    val done = all.flatMap { (_, tiles) -> tiles.filter { it.left == 0 } }
    return (active + TileSection(SECTION_DONE, done)).filter { it.tiles.isNotEmpty() }
}

/** Each section as a header and a horizontal row of tiles (grid) or its first few rows (list). */
@Composable
private fun Overview(
    sections: List<TileSection>,
    listLayout: Boolean,
    scanCard: @Composable () -> Unit,
    showEmpty: Boolean,
    onOpen: (Folder) -> Unit,
    onSeeAll: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "scan") { scanCard() }
        if (sections.isEmpty() && showEmpty) {
            item {
                Text(
                    "No categories yet. They show up once a few photos share a tag.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for (section in sections) {
            val done = section.title == SECTION_DONE
            val previewCount = if (listLayout) LIST_PREVIEW else Int.MAX_VALUE
            item(key = "header-${section.title}") {
                SectionHeader(
                    title = section.title,
                    detail = if (done) "${section.tiles.size} sorted" else null,
                    // the done section stays collapsed: just a way in
                    onSeeAll = if (done || section.tiles.size > previewCount || !listLayout) {
                        { onSeeAll(section.title) }
                    } else null,
                )
            }
            if (done) continue
            if (listLayout) {
                items(section.tiles.take(LIST_PREVIEW), key = { "${section.title}-${it.folder.id}" }) { tile ->
                    FolderRow(
                        tile.folder,
                        tileSubtitle(context, tile),
                        onOpen,
                        Modifier.padding(horizontal = 12.dp),
                    )
                }
            } else {
                item(key = "row-${section.title}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(section.tiles, key = { it.folder.id }) { tile ->
                            FolderCard(tile.folder, tileSubtitle(context, tile), onOpen, Modifier.width(140.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, detail: String?, onSeeAll: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onSeeAll != null) Modifier.clickable(onClick = onSeeAll) else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) {
            Text(
                " · $detail",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            Text(
                if (detail != null) "Show" else "See all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TileGrid(tiles: List<Tile>, listLayout: Boolean, emptyText: String, onOpen: (Folder) -> Unit) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = folderColumns(listLayout),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = folderSpacing(listLayout),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (tiles.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    emptyText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp, start = 4.dp),
                )
            }
        }
        items(tiles, key = { it.folder.id }) { tile ->
            FolderItem(tile.folder, tileSubtitle(context, tile), listLayout, onOpen)
        }
    }
}

/** "240 photos · 1.2 GB", "40 of 240 left · 200 MB" or "All 240 sorted". */
private fun tileSubtitle(context: Context, tile: Tile): String {
    val total = tile.folder.mediaIds.size
    return when (tile.left) {
        0 -> "All ${formatCount(total)} sorted"
        total -> "${formatCount(total)} ${if (total == 1) "photo" else "photos"} · ${formatSize(context, tile.leftBytes)}"
        else -> "${formatCount(tile.left)} of ${formatCount(total)} left · ${formatSize(context, tile.leftBytes)}"
    }
}

@Composable
private fun TagSearchField(query: String, onChange: (String) -> Unit, onFocusChange: (Boolean) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search tags: dog, beach, receipt…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp)
            .onFocusChanged { onFocusChange(it.isFocused) },
    )
}

/** Shown while the search field is focused and empty, so people see which tags exist. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PopularTags(tags: List<String>, onPick: (String) -> Unit) {
    if (tags.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Common in your photos",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                AssistChip(onClick = { onPick(tag) }, label = { Text(tag) })
            }
        }
    }
}

@Composable
private fun ScanCard(
    state: ScanState,
    analyzed: Int,
    total: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val intro = state is ScanState.Idle && analyzed == 0
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                state is ScanState.Failed -> colors.errorContainer
                intro -> colors.primaryContainer
                else -> colors.surfaceContainerHigh
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            when (state) {
                ScanState.Downloading -> {
                    Text("Getting ready", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Downloading the photo model (66 MB). This happens once.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onStop, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
                }
                is ScanState.Scanning -> {
                    val progress = state.done.toFloat() / state.total.coerceAtLeast(1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Analyzing photos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${formatCount(state.done)} of ${formatCount(state.total)}" +
                                    (state.msPerPhoto?.let { " · ${timeLeft((state.total - state.done) * it)}" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Categories fill in as it goes",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = onStop) { Text("Pause") }
                    }
                }
                is ScanState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onStart, modifier = Modifier.align(Alignment.End)) { Text("Try again") }
                }
                ScanState.Idle -> if (intro) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).background(colors.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onPrimary)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Sort photos by what's in them", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Finds screenshots, receipts, blurry shots, closed eyes and more.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Runs on this phone. Photos are never uploaded.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text("Analyze ${formatCount(total)} photos")
                    }
                    Text(
                        "The first run downloads a 66 MB model",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
                    )
                } else {
                    val remaining = total - analyzed
                    Text(
                        if (remaining <= AUTO_SCAN_MAX) "${formatCount(remaining)} new ${if (remaining == 1) "photo" else "photos"} to analyze"
                        else "Analysis paused",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { analyzed.toFloat() / total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatCount(analyzed)} of ${formatCount(total)} analyzed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(onClick = onStart) { Text(if (remaining <= AUTO_SCAN_MAX) "Analyze" else "Resume") }
                    }
                }
            }
        }
    }
}
