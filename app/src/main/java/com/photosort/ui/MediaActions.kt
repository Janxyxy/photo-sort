package com.photosort.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.photosort.Media
import com.photosort.MediaDetails
import com.photosort.MediaRepository
import java.util.Locale

private fun Media.mimeType(details: MediaDetails? = null) = details?.mimeType ?: if (isVideo) "video/*" else "image/*"

fun shareMedia(context: Context, media: Media) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = media.mimeType()
        putExtra(Intent.EXTRA_STREAM, media.uri)
        // the chooser only passes the read grant on when the uri is also in the clip data
        clipData = ClipData.newRawUri(null, media.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Opens the item in the default gallery / viewer app. */
fun openInGallery(context: Context, media: Media, details: MediaDetails? = null) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(media.uri, media.mimeType(details))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No gallery app found", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Returns a function that stars or unstars an item in the gallery (MediaStore's favourite flag).
 * Android asks for confirmation each time unless the app is allowed to manage media.
 * [onDone] gets the item and its new state, only when the change went through.
 */
@Composable
fun rememberFavoriteToggle(onDone: (media: Media, favorite: Boolean) -> Unit): (Media) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Media?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val media = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (result.resultCode == Activity.RESULT_OK) onDone(media, !media.isFavorite)
    }
    return { media ->
        if (pending == null) {
            pending = media
            val request = MediaStore.createFavoriteRequest(context.contentResolver, listOf(media.uri), !media.isFavorite)
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(media: Media, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var details by remember(media.id) { mutableStateOf<MediaDetails?>(null) }
    LaunchedEffect(media.id) { details = MediaRepository.loadDetails(context, media) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            val d = details
            // selectable so the name or path can be copied
            SelectionContainer {
                Column {
                    Text(d?.name ?: "…", style = MaterialTheme.typography.titleLarge)
                    if (d != null) {
                        val storage = if (d.onSdCard) "SD card" else "Internal storage"
                        InfoRow("Location", "$storage/${d.folder}".trimEnd('/'))
                    }
                    InfoRow(if (media.takenMs > 0) "Taken" else "Added", formatDateTime(media.displayDateMs))
                    if (d != null && d.modifiedMs > 0 && kotlin.math.abs(d.modifiedMs - media.displayDateMs) > 60_000) {
                        InfoRow("Modified", formatDateTime(d.modifiedMs))
                    }
                    InfoRow("Size", formatSize(context, media.size))
                    if (d != null && d.width > 0 && d.height > 0) {
                        val mp = d.width.toLong() * d.height / 1_000_000.0
                        val extra = if (media.isVideo) "" else " · %.1f MP".format(Locale.getDefault(), mp)
                        InfoRow("Resolution", "${d.width} × ${d.height}$extra")
                    }
                    if (media.isVideo) InfoRow("Length", formatDuration(media.durationMs))
                    d?.mimeType?.let { InfoRow("Type", it) }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                OutlinedButton(onClick = { openInGallery(context, media, d) }, modifier = Modifier.weight(1f)) {
                    Text("Open in gallery")
                }
                Button(onClick = { shareMedia(context, media) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("Share", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(top = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
