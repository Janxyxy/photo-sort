package com.photosort.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.photosort.MediaRepository
import com.photosort.Prefs
import com.photosort.app
import com.photosort.eye.ToggleableCategory
import com.photosort.eye.allCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turn categories and tags on or off; off ones don't show on the Categories tab or in search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<Pair<String, List<ToggleableCategory>>>?>(null) }
    var hidden by remember { mutableStateOf(prefs.hiddenCategories) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val photos = MediaRepository.loadAllImages(context)
        val tags = context.app.eyeIndex.snapshot()
        groups = withContext(Dispatchers.Default) { allCategories(photos, tags) }
    }

    fun set(title: String, on: Boolean) {
        hidden = if (on) hidden - title else hidden + title
        prefs.hiddenCategories = hidden
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hidden.isNotEmpty()) {
                        TextButton(onClick = {
                            hidden = emptySet()
                            prefs.hiddenCategories = hidden
                        }) { Text("Turn all on") }
                    }
                },
            )
        },
    ) { padding ->
        val all = groups
        if (all == null) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }
        val q = filter.trim().lowercase()
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Find a category or tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            for ((section, categories) in all) {
                val shown = if (q.isEmpty()) categories else categories.filter { it.title.lowercase().contains(q) }
                if (shown.isEmpty()) continue
                item(key = "section-$section") {
                    Text(
                        section,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(shown, key = { "$section-${it.title}" }) { category ->
                    val on = category.title !in hidden
                    ListItem(
                        headlineContent = { Text(category.title) },
                        supportingContent = { Text(if (category.count == 1) "1 photo" else "${formatCount(category.count)} photos") },
                        trailingContent = { Switch(checked = on, onCheckedChange = { set(category.title, it) }) },
                        modifier = Modifier.clickable { set(category.title, !on) },
                    )
                }
            }
        }
    }
}
