package com.fotosort.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fotosort.Folder
import com.fotosort.MediaRepository
import com.fotosort.Photo
import com.fotosort.Prefs
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(prefs: Prefs, folder: Folder, onBack: () -> Unit) {
    val context = LocalContext.current
    val photos = remember { mutableStateListOf<Photo>() }
    var loaded by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var marked by remember { mutableStateOf(prefs.pendingIds.toSet()) }
    var keptThisSession by remember { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(folder.id) {
        val all = MediaRepository.loadPhotos(context, folder.id)
        photos += if (prefs.skipKept) all.filter { it.id !in prefs.keptIds } else all
        loaded = true
    }

    val trash = rememberTrashLauncher(prefs) { trashed ->
        if (trashed.isNotEmpty()) {
            val removedBefore = photos.take(index).count { it.id in trashed }
            photos.removeAll { it.id in trashed }
            index = (index - removedBefore).coerceIn(0, photos.size)
        }
        marked = prefs.pendingIds.toSet()
        if (leaving) onBack()
    }

    // Leaving the folder asks to empty the pending batch; cancelled photos stay marked for later.
    fun leave() {
        if (prefs.pendingIds.isEmpty()) {
            onBack()
        } else {
            leaving = true
            trash()
        }
    }
    BackHandler { leave() }

    fun decide(keep: Boolean) {
        val photo = photos.getOrNull(index) ?: return
        if (keep) {
            prefs.markKept(photo.id)
            keptThisSession++
        } else {
            prefs.markForTrash(photo.id)
        }
        marked = prefs.pendingIds.toSet()
        index++
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(folder.name, style = MaterialTheme.typography.titleMedium)
                        if (photos.isNotEmpty()) {
                            Text(
                                "${minOf(index + 1, photos.size)} / ${photos.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (marked.isNotEmpty()) {
                        TextButton(onClick = trash, colors = ButtonDefaults.textButtonColors(contentColor = TrashColor)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Move marked photos to trash")
                            Text("${marked.size}", Modifier.padding(start = 4.dp))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                photos.isEmpty() -> Text(
                    "Nothing left to sort here",
                    Modifier.align(Alignment.Center),
                    color = Color.White,
                )
                index >= photos.size -> DoneState(
                    kept = keptThisSession,
                    markedCount = marked.size,
                    onTrash = trash,
                    onReview = { index = photos.size - 1 },
                    onBack = ::leave,
                )
                else -> Column(Modifier.fillMaxSize()) {
                    SwipeDeck(
                        current = photos[index],
                        next = photos.getOrNull(index + 1),
                        previous = photos.getOrNull(index - 1),
                        isMarked = photos[index].id in marked,
                        modifier = Modifier.weight(1f),
                        onKeep = { decide(keep = true) },
                        onTrash = { decide(keep = false) },
                        onNext = { index++ },
                        onPrevious = { if (index > 0) index-- },
                    )
                    DecisionBar(onKeep = { decide(keep = true) }, onTrash = { decide(keep = false) })
                }
            }
        }
    }
}

private enum class Axis { Horizontal, Vertical }

/**
 * Left = keep, right = trash, up = next photo, down = previous photo.
 * The neighbouring photo is drawn underneath so it is already loaded when the top card leaves.
 */
@Composable
private fun SwipeDeck(
    current: Photo,
    next: Photo?,
    previous: Photo?,
    isMarked: Boolean,
    modifier: Modifier,
    onKeep: () -> Unit,
    onTrash: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(current.id) { Animatable(0f) }
    val offsetY = remember(current.id) { Animatable(0f) }

    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val thresholdX = width * 0.25f
        val thresholdY = height * 0.12f

        fun progress() = maxOf(abs(offsetX.value) / thresholdX, abs(offsetY.value) / thresholdY).coerceAtMost(1f)

        // Underneath: the previous photo while dragging down, otherwise the next one
        previous?.let { photo ->
            key(photo.id) {
                PhotoCard(photo, Modifier.graphicsLayer { alpha = if (offsetY.value > 0f) 1f else 0f })
            }
        }
        next?.let { photo ->
            key(photo.id) {
                PhotoCard(
                    photo,
                    Modifier.graphicsLayer {
                        val scale = 0.92f + 0.08f * progress()
                        scaleX = scale
                        scaleY = scale
                        alpha = if (offsetY.value > 0f) 0f else 1f
                    },
                )
            }
        }

        key(current.id) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value
                        rotationZ = offsetX.value / width * 12f
                    }
                    .pointerInput(current.id) {
                        var axis: Axis? = null
                        detectDragGestures(
                            onDragStart = { axis = null },
                            onDragCancel = {
                                scope.launch { offsetX.animateTo(0f) }
                                scope.launch { offsetY.animateTo(0f) }
                            },
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        axis == Axis.Horizontal && offsetX.value < -thresholdX -> {
                                            offsetX.animateTo(-width * 1.5f)
                                            onKeep()
                                        }
                                        axis == Axis.Horizontal && offsetX.value > thresholdX -> {
                                            offsetX.animateTo(width * 1.5f)
                                            onTrash()
                                        }
                                        axis == Axis.Vertical && offsetY.value < -thresholdY -> {
                                            offsetY.animateTo(-height)
                                            onNext()
                                        }
                                        axis == Axis.Vertical && offsetY.value > thresholdY && previous != null -> {
                                            offsetY.animateTo(height)
                                            onPrevious()
                                        }
                                        else -> {
                                            launch { offsetX.animateTo(0f) }
                                            offsetY.animateTo(0f)
                                        }
                                    }
                                }
                            },
                        ) { change, drag ->
                            change.consume()
                            if (axis == null) axis = if (abs(drag.x) > abs(drag.y)) Axis.Horizontal else Axis.Vertical
                            scope.launch {
                                if (axis == Axis.Horizontal) offsetX.snapTo(offsetX.value + drag.x)
                                else offsetY.snapTo(offsetY.value + drag.y)
                            }
                        }
                    },
            ) {
                PhotoCard(current, Modifier)
                SwipeLabel(
                    "KEEP",
                    KeepColor,
                    Modifier.align(Alignment.TopEnd).graphicsLayer { alpha = (-offsetX.value / thresholdX).coerceIn(0f, 1f) },
                )
                SwipeLabel(
                    "DELETE",
                    TrashColor,
                    Modifier.align(Alignment.TopStart).graphicsLayer { alpha = (offsetX.value / thresholdX).coerceIn(0f, 1f) },
                )
                if (isMarked) {
                    Text(
                        "Marked for trash · swipe left to keep",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(TrashColor, RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(photo: Photo, modifier: Modifier) {
    AsyncImage(
        model = photo.uri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111111)),
    )
}

@Composable
private fun SwipeLabel(text: String, color: Color, modifier: Modifier) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier
            .padding(24.dp)
            .border(3.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun DecisionBar(onKeep: () -> Unit, onTrash: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onKeep,
                colors = ButtonDefaults.buttonColors(containerColor = KeepColor, contentColor = Color.White),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text("Keep", Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onTrash,
                colors = ButtonDefaults.buttonColors(containerColor = TrashColor, contentColor = Color.White),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("Delete", Modifier.padding(start = 8.dp))
            }
        }
        Text(
            "← keep · delete →   ·   swipe up/down to browse",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
        )
    }
}

@Composable
private fun DoneState(kept: Int, markedCount: Int, onTrash: () -> Unit, onReview: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("All done", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Kept $kept · $markedCount marked for trash", color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        if (markedCount > 0) {
            Button(
                onClick = onTrash,
                colors = ButtonDefaults.buttonColors(containerColor = TrashColor, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("Move $markedCount to trash", Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onReview) { Text("Back to last photo") }
        TextButton(onClick = onBack) { Text("Back to folders") }
    }
}
