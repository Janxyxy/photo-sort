package com.photosort.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.photosort.Prefs
import com.photosort.SortOrder
import com.photosort.ThemeMode
import com.photosort.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class SettingsPage(val title: String) {
    Sorting("Sorting"),
    Trash("Trash"),
    Analysis("Photo analysis"),
    Appearance("Appearance"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf<SettingsPage?>(null) }
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
    var storage by remember { mutableStateOf<AnalysisStorage?>(null) }
    var hiddenCount by remember { mutableIntStateOf(prefs.hiddenCategories.size) }
    var managingCategories by remember { mutableStateOf(false) }
    // Android 12+: with "media management" allowed, favourites and trash skip the system confirmation
    val canAskManageMedia = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var managesMedia by remember {
        mutableStateOf(canAskManageMedia && MediaStore.canManageMedia(context))
    }
    val manageMediaSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        managesMedia = canAskManageMedia && MediaStore.canManageMedia(context)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { analyzedCount = context.app.eyeIndex.snapshot().size }
    LaunchedEffect(analyzedCount) { storage = analysisStorage(context) }

    if (managingCategories) {
        CategoryManagerScreen(prefs, onBack = {
            managingCategories = false
            hiddenCount = prefs.hiddenCategories.size
        })
        return
    }

    val close = { if (page != null) page = null else onBack() }
    BackHandler(onBack = close)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page?.title ?: "Settings") },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            when (page) {
                null -> {
                    PageLink(
                        SettingsPage.Sorting,
                        "${if (rightKeeps) "Right" else "Left"} keeps · ${sortOrder.label.lowercase()}",
                    ) { page = it }
                    PageLink(
                        SettingsPage.Trash,
                        "${formatSize(context, prefs.freedBytes)} freed so far",
                    ) { page = it }
                    PageLink(
                        SettingsPage.Analysis,
                        buildString {
                            append("${formatCount(analyzedCount)} photos analyzed")
                            if (hiddenCount > 0) append(" · $hiddenCount categories off")
                        },
                    ) { page = it }
                    PageLink(SettingsPage.Appearance, prefs.theme.label) { page = it }
                    HorizontalDivider()
                    Text(
                        "Swipe ${if (rightKeeps) "right" else "left"} to keep, ${if (rightKeeps) "left" else "right"} to delete, " +
                            "up and down to browse without deciding. Pinch or double-tap to zoom. Tap the date to see " +
                            "where a photo is stored. Deleted items go to the system trash, where Android keeps them for 30 days.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                SettingsPage.Sorting -> {
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
                    SectionTitle("Kept photos")
                    SwitchItem(
                        title = "Skip photos I already kept",
                        summary = "Photos you kept are hidden the next time you open the folder",
                        checked = skipKept,
                        onChange = {
                            skipKept = it
                            prefs.skipKept = it
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Clear kept history") },
                        supportingContent = { Text("$keptCount items remembered as kept") },
                        modifier = Modifier.clickable(enabled = keptCount > 0) { confirmClear = true },
                    )
                }

                SettingsPage.Trash -> {
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
                    if (canAskManageMedia) {
                        SwitchItem(
                            title = "Skip confirmation dialogs",
                            summary = "Star and move to trash without Android asking every time. " +
                                "Opens system settings (Media management apps).",
                            checked = managesMedia,
                            onChange = {
                                manageMediaSettings.launch(
                                    Intent(
                                        android.provider.Settings.ACTION_REQUEST_MANAGE_MEDIA,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            },
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Space freed") },
                        supportingContent = { Text("${formatSize(context, prefs.freedBytes)} moved to the trash so far") },
                    )
                }

                SettingsPage.Analysis -> {
                    ListItem(
                        headlineContent = { Text("Categories and tags") },
                        supportingContent = {
                            Text(if (hiddenCount == 0) "All shown" else "$hiddenCount turned off")
                        },
                        modifier = Modifier.clickable { managingCategories = true },
                    )
                    ListItem(
                        headlineContent = { Text("Space used") },
                        supportingContent = {
                            val s = storage
                            Text(
                                if (s == null) {
                                    "…"
                                } else {
                                    "Tags ${formatSize(context, s.tagBytes)} · model ${formatSize(context, s.modelBytes)} · " +
                                        "thumbnails ${formatSize(context, s.thumbnailBytes)}"
                                },
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Clear photo analysis") },
                        supportingContent = { Text("$analyzedCount photos analyzed for categories") },
                        modifier = Modifier.clickable(enabled = analyzedCount > 0) { confirmClearAnalysis = true },
                    )
                }

                SettingsPage.Appearance -> {
                    ThemeMode.entries.forEach { mode ->
                        RadioItem(mode.label, selected = mode == prefs.theme) { prefs.theme = mode }
                    }
                }
            }
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

private class AnalysisStorage(val tagBytes: Long, val modelBytes: Long, val thumbnailBytes: Long)

/** Coil's disk cache folder; everything else in the cache is Eye's downloaded model. */
private const val COIL_CACHE_DIR = "image_cache"

private suspend fun analysisStorage(context: android.content.Context) = withContext(Dispatchers.IO) {
    fun File.bytes(): Long = walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    val cache = context.cacheDir
    val thumbnails = File(cache, COIL_CACHE_DIR).bytes()
    AnalysisStorage(
        tagBytes = File(context.filesDir, "eye_tags.jsonl").length(),
        modelBytes = cache.bytes() - thumbnails,
        thumbnailBytes = thumbnails,
    )
}

@Composable
private fun PageLink(page: SettingsPage, summary: String, onOpen: (SettingsPage) -> Unit) {
    ListItem(
        headlineContent = { Text(page.title) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable { onOpen(page) },
    )
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
