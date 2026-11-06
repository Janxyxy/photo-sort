package com.fotosort.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fotosort.Folder
import com.fotosort.Media
import com.fotosort.MediaRepository
import com.fotosort.Prefs
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(prefs: Prefs, folder: Folder, onBack: () -> Unit) {
    val context = LocalContext.current
    val items = remember { mutableStateListOf<Media>() }
    var loaded by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var marked by remember { mutableStateOf(prefs.pendingIds.toSet()) }
    var keptThisSession by remember { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf(false) }
    var similarGroup by remember { mutableStateOf<List<Media>?>(null) }
    var playing by remember { mutableStateOf<Media?>(null) }
    val history = remember { mutableStateListOf<Step>() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val groupSimilar = remember { prefs.groupSimilar }
    val rightKeeps = remember { prefs.swipeRightToKeep }
    val haptics = remember { prefs.haptics }
    val similar by remember { derivedStateOf { if (groupSimilar) similarGroups(items) else emptyMap() } }

    LaunchedEffect(folder.id) {
        val all = MediaRepository.loadFolder(context, folder, prefs.includeVideos, prefs.sortOrder)
        items += if (prefs.skipKept) all.filter { it.id !in prefs.keptIds } else all
        loaded = true
    }

    fun undo() {
        val last = history.removeLastOrNull() ?: return
        for (d in last.decisions) {
            prefs.restore(d.id, d.wasKept, d.wasPending)
            if (d.keep) keptThisSession--
        }
        marked = prefs.pendingIds.toSet()
        index = last.index
        snackbar.currentSnackbarData?.dismiss()
    }

    fun notify(message: String, withUndo: Boolean = false) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message,
                actionLabel = if (withUndo) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    fun handleTrashed(trashed: Set<Long>, bytes: Long) {
        if (trashed.isNotEmpty()) {
            // indexes in the history no longer match once items leave the list
            history.clear()
            val removedBefore = items.take(index).count { it.id in trashed }
            items.removeAll { it.id in trashed }
            index = (index - removedBefore).coerceIn(0, items.size)
            notify("Freed ${formatSize(context, bytes)}")
        }
        marked = prefs.pendingIds.toSet()
    }

    val trash = rememberTrashLauncher(prefs) { trashed, bytes ->
        handleTrashed(trashed, bytes)
        if (leaving) onBack()
    }

    // Leaving the folder asks to empty the pending batch (if enabled); cancelled items stay marked for later.
    fun leave() {
        if (prefs.pendingIds.isEmpty() || !prefs.trashOnLeave) {
            onBack()
        } else {
            leaving = true
            trash()
        }
    }
    BackHandler(enabled = !showReview && !bulk) { leave() }

    fun record(decisions: List<Pair<Media, Boolean>>, nextIndex: Int, message: String) {
        history += Step(
            index,
            decisions.map { (media, keep) ->
                Decision(media.id, keep, media.id in prefs.keptIds, media.id in prefs.pendingIds)
            },
        )
        if (history.size > MAX_UNDO) history.removeAt(0)
        for ((media, keep) in decisions) {
            if (keep) {
                prefs.markKept(media.id)
                keptThisSession++
            } else {
                prefs.markForTrash(media.id)
            }
        }
        marked = prefs.pendingIds.toSet()
        index = nextIndex
        notify(message, withUndo = true)
    }

    fun decide(keep: Boolean) {
        val media = items.getOrNull(index) ?: return
        record(listOf(media to keep), index + 1, if (keep) "Kept" else "Marked for trash")
    }

    if (showReview) {
        ReviewScreen(
            prefs = prefs,
            onClose = {
                showReview = false
                marked = prefs.pendingIds.toSet()
            },
            onTrashed = { trashed, bytes ->
                showReview = false
                handleTrashed(trashed, bytes)
            },
        )
        return
    }

    if (bulk) {
        val remaining = items.drop(index)
        BulkSelectScreen(
            items = remaining,
            // categories are mostly clutter (screenshots, blurry...), so start with everything selected
            preselectAll = folder.virtual,
            onClose = { bulk = false },
            onApply = { trashIds ->
                bulk = false
                val trashCount = remaining.count { it.id in trashIds }
                record(
                    remaining.map { it to (it.id !in trashIds) },
                    items.size,
                    "Kept ${remaining.size - trashCount}, marked $trashCount for trash",
                )
            },
        )
        return
    }

    // photos read best on black, so this screen stays dark whatever the app theme is
    FotoSortTheme(dark = true) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                Column {
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
                                if (items.isNotEmpty()) {
                                    Text(
                                        "${minOf(index + 1, items.size)} / ${items.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        actions = {
                            if (items.size - index > 1) {
                                IconButton(onClick = { bulk = true }) {
                                    Icon(GridIcon, contentDescription = "Select several")
                                }
                            }
                            if (history.isNotEmpty()) {
                                TextButton(onClick = ::undo) { Text("Undo") }
                            }
                            if (marked.isNotEmpty()) {
                                TextButton(
                                    onClick = { showReview = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = TrashColor),
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Review items marked for trash")
                                    Text("${marked.size}", Modifier.padding(start = 4.dp))
                                }
                            }
                        },
                    )
                    if (items.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { index.toFloat() / items.size },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when {
                    !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    items.isEmpty() -> Text(
                        "Nothing left to sort here",
                        Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                    index >= items.size -> {
                        val markedHere = items.filter { it.id in marked }
                        DoneState(
                            kept = keptThisSession,
                            markedCount = markedHere.size,
                            markedBytes = markedHere.sumOf { it.size },
                            onReview = { showReview = true },
                            onTrash = trash,
                            onLast = { index = items.size - 1 },
                            onBack = ::leave,
                        )
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        val current = items[index]
                        val group = similar[current.id]
                        SwipeDeck(
                            current = current,
                            next = items.getOrNull(index + 1),
                            previous = items.getOrNull(index - 1),
                            isMarked = current.id in marked,
                            similarCount = group?.size ?: 0,
                            rightKeeps = rightKeeps,
                            haptics = haptics,
                            modifier = Modifier.weight(1f),
                            onKeep = { decide(keep = true) },
                            onTrash = { decide(keep = false) },
                            onNext = { index++ },
                            onPrevious = { if (index > 0) index-- },
                            onOpenSimilar = { similarGroup = group },
                            onPlay = { playing = current },
                        )
                        DecisionBar(rightKeeps, onKeep = { decide(keep = true) }, onTrash = { decide(keep = false) })
                    }
                }
                // at the top so it doesn't cover the Keep / Delete buttons
                SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter))
            }
        }

        similarGroup?.let { group ->
            SimilarSheet(
                group = group,
                onDismiss = { similarGroup = null },
                onApply = { keepIds ->
                    val ids = group.mapTo(HashSet()) { it.id }
                    var next = index
                    while (next < items.size && items[next].id in ids) next++
                    val trashCount = group.size - keepIds.size
                    record(
                        group.map { it to (it.id in keepIds) },
                        next,
                        "Kept ${keepIds.size}, marked $trashCount for trash",
                    )
                    similarGroup = null
                },
            )
        }
        playing?.let { VideoPlayerDialog(it, onDismiss = { playing = null }) }
    }
}

private const val MAX_UNDO = 50
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Longest side for the zoomed decode; caps memory for very large photos. */
private const val ZOOM_DECODE_SIZE = 4096
private val CardColor = Color(0xFF111111)

private data class Decision(val id: Long, val keep: Boolean, val wasKept: Boolean, val wasPending: Boolean)

/** One undoable action: a single swipe, or a whole similar-photos group. */
private data class Step(val index: Int, val decisions: List<Decision>)

private enum class DragMode { Horizontal, Vertical, Pan, Transform }

private fun View.thresholdHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        } else {
            HapticFeedbackConstants.CONFIRM
        },
    )
}

/**
 * Left = keep, right = trash (mirrored when [rightKeeps]), up = next, down = previous. Pinch or double-tap zooms; while zoomed,
 * dragging pans instead of swiping. The neighbouring item is drawn underneath so it is already
 * loaded when the top card leaves.
 */
@Composable
private fun SwipeDeck(
    current: Media,
    next: Media?,
    previous: Media?,
    isMarked: Boolean,
    similarCount: Int,
    rightKeeps: Boolean,
    haptics: Boolean,
    modifier: Modifier,
    onKeep: () -> Unit,
    onTrash: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenSimilar: () -> Unit,
    onPlay: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val offsetX = remember(current.id) { Animatable(0f) }
    val offsetY = remember(current.id) { Animatable(0f) }
    var zoom by remember(current.id) { mutableFloatStateOf(1f) }
    var pan by remember(current.id) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val thresholdX = width * 0.25f
        val thresholdY = height * 0.12f

        fun progress() = maxOf(abs(offsetX.value) / thresholdX, abs(offsetY.value) / thresholdY).coerceAtMost(1f)

        fun clampPan(p: Offset, z: Float): Offset {
            val maxX = width * (z - 1f) / 2f
            val maxY = height * (z - 1f) / 2f
            return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
        }

        suspend fun settle(mode: DragMode) {
            when {
                mode == DragMode.Horizontal && offsetX.value < -thresholdX -> {
                    offsetX.animateTo(-width * 1.5f)
                    if (rightKeeps) onTrash() else onKeep()
                }
                mode == DragMode.Horizontal && offsetX.value > thresholdX -> {
                    offsetX.animateTo(width * 1.5f)
                    if (rightKeeps) onKeep() else onTrash()
                }
                mode == DragMode.Vertical && offsetY.value < -thresholdY -> {
                    offsetY.animateTo(-height)
                    onNext()
                }
                mode == DragMode.Vertical && offsetY.value > thresholdY && previous != null -> {
                    offsetY.animateTo(height)
                    onPrevious()
                }
                else -> {
                    scope.launch { offsetX.animateTo(0f) }
                    offsetY.animateTo(0f)
                }
            }
        }

        // Underneath: the previous item while dragging down, otherwise the next one
        previous?.let { media ->
            key(media.id) {
                MediaCard(media, Modifier.graphicsLayer { alpha = if (offsetY.value > 0f) 1f else 0f })
            }
        }
        next?.let { media ->
            key(media.id) {
                MediaCard(
                    media,
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .pointerInput(current.id) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                if (zoom > 1f) {
                                    zoom = 1f
                                    pan = Offset.Zero
                                } else {
                                    // keep the tapped point under the finger
                                    val center = Offset(width / 2f, height / 2f)
                                    zoom = DOUBLE_TAP_ZOOM
                                    pan = clampPan((center - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                                }
                            },
                            onTap = { if (current.isVideo) onPlay() },
                        )
                    }
                    .pointerInput(current.id, width, height) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var mode: DragMode? = null
                            var travel = Offset.Zero
                            var dx = 0f
                            var dy = 0f
                            var pastThreshold = false

                            fun updateThreshold(past: Boolean) {
                                if (haptics && past && !pastThreshold) view.thresholdHaptic()
                                pastThreshold = past
                            }

                            do {
                                val event = awaitPointerEvent()
                                val panDelta = event.calculatePan()
                                if (event.changes.count { it.pressed } > 1 && (mode == null || mode == DragMode.Pan)) {
                                    mode = DragMode.Transform
                                }
                                when (mode) {
                                    null -> {
                                        travel += panDelta
                                        if (travel.getDistance() > viewConfiguration.touchSlop) {
                                            mode = when {
                                                zoom > 1f -> DragMode.Pan
                                                abs(travel.x) > abs(travel.y) -> DragMode.Horizontal.also { dx = travel.x }
                                                else -> DragMode.Vertical.also { dy = travel.y }
                                            }
                                        }
                                    }
                                    DragMode.Transform -> {
                                        val z = (zoom * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                                        zoom = z
                                        pan = clampPan(pan + panDelta, z)
                                    }
                                    DragMode.Pan -> pan = clampPan(pan + panDelta, zoom)
                                    DragMode.Horizontal -> {
                                        dx += panDelta.x
                                        val target = dx
                                        scope.launch { offsetX.snapTo(target) }
                                        updateThreshold(abs(dx) > thresholdX)
                                    }
                                    DragMode.Vertical -> {
                                        dy += panDelta.y
                                        val target = dy
                                        scope.launch { offsetY.snapTo(target) }
                                        updateThreshold(if (dy > 0) dy > thresholdY && previous != null else -dy > thresholdY)
                                    }
                                }
                                if (mode != null) event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })

                            when (val finished = mode) {
                                DragMode.Horizontal, DragMode.Vertical -> scope.launch { settle(finished) }
                                DragMode.Transform, DragMode.Pan -> if (zoom < 1.05f) {
                                    zoom = 1f
                                    pan = Offset.Zero
                                }
                                null -> Unit
                            }
                        }
                    },
            ) {
                val zoomLayer = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    }
                AsyncImage(
                    model = current.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = zoomLayer,
                )
                // The image above is decoded at card size and turns blurry when scaled up.
                // While zoomed, a sharper copy loads on top; the card-sized one shows until it's ready.
                if (zoom > 1f && !current.isVideo) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = remember(current.id) {
                            ImageRequest.Builder(context).data(current.uri).size(ZOOM_DECODE_SIZE).build()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = zoomLayer,
                    )
                }
                if (current.isVideo) PlayBadge(Modifier.align(Alignment.Center))
                // each label sits on the side the card moves away from
                val keepSign = if (rightKeeps) 1f else -1f
                SwipeLabel(
                    "KEEP",
                    KeepColor,
                    Modifier
                        .align(if (rightKeeps) Alignment.TopStart else Alignment.TopEnd)
                        .graphicsLayer { alpha = (keepSign * offsetX.value / thresholdX).coerceIn(0f, 1f) },
                )
                SwipeLabel(
                    "DELETE",
                    TrashColor,
                    Modifier
                        .align(if (rightKeeps) Alignment.TopEnd else Alignment.TopStart)
                        .graphicsLayer { alpha = (-keepSign * offsetX.value / thresholdX).coerceIn(0f, 1f) },
                )
                if (isMarked) {
                    Pill(
                        "Marked for trash · swipe ${if (rightKeeps) "right" else "left"} to keep",
                        TrashColor,
                        Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    )
                }
                InfoPill(current, Modifier.align(Alignment.BottomStart).padding(12.dp))
                if (similarCount > 1) {
                    Pill(
                        "$similarCount similar",
                        MaterialTheme.colorScheme.primary,
                        Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        textColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = onOpenSimilar,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCard(media: Media, modifier: Modifier) {
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(CardColor)) {
        AsyncImage(
            model = media.uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (media.isVideo) PlayBadge(Modifier.align(Alignment.Center))
    }
}

@Composable
private fun PlayBadge(modifier: Modifier) {
    Icon(
        Icons.Filled.PlayArrow,
        contentDescription = "Play video",
        tint = Color.White,
        modifier = modifier
            .size(64.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .padding(12.dp),
    )
}

@Composable
private fun InfoPill(media: Media, modifier: Modifier) {
    val context = LocalContext.current
    val parts = buildList {
        add(formatDate(media.displayDateMs))
        add(formatSize(context, media.size))
        if (media.isVideo) add(formatDuration(media.durationMs))
    }
    Pill(parts.joinToString(" · "), Color.Black.copy(alpha = 0.55f), modifier)
}

@Composable
private fun Pill(
    text: String,
    color: Color,
    modifier: Modifier,
    textColor: Color = Color.White,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text,
        color = textColor,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
private fun DecisionBar(rightKeeps: Boolean, onKeep: () -> Unit, onTrash: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // each button sits on the side of its swipe direction
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val keep = @Composable {
                Button(
                    onClick = onKeep,
                    colors = ButtonDefaults.buttonColors(containerColor = KeepColor, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text("Keep", Modifier.padding(start = 8.dp))
                }
            }
            val delete = @Composable {
                Button(
                    onClick = onTrash,
                    colors = ButtonDefaults.buttonColors(containerColor = TrashColor, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("Delete", Modifier.padding(start = 8.dp))
                }
            }
            if (rightKeeps) {
                delete()
                keep()
            } else {
                keep()
                delete()
            }
        }
        Text(
            if (rightKeeps) {
                "← delete · keep →   ·   swipe up/down to browse"
            } else {
                "← keep · delete →   ·   swipe up/down to browse"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
        )
    }
}

@Composable
private fun DoneState(
    kept: Int,
    markedCount: Int,
    markedBytes: Long,
    onReview: () -> Unit,
    onTrash: () -> Unit,
    onLast: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("All done", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Kept $kept · $markedCount marked for trash", color = Color.Gray)
        if (markedCount > 0) {
            Text("Deleting them frees ${formatSize(context, markedBytes)}", color = Color.Gray)
        }
        Spacer(Modifier.height(24.dp))
        if (markedCount > 0) {
            Button(
                onClick = onTrash,
                colors = ButtonDefaults.buttonColors(containerColor = TrashColor, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("Move to trash", Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReview) { Text("Review marked") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onLast) { Text("Back to last photo") }
        TextButton(onClick = onBack) { Text("Back to folders") }
    }
}
