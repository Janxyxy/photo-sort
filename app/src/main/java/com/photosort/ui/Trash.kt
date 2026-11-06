package com.photosort.ui

import android.app.Activity
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.photosort.Media
import com.photosort.MediaRepository
import com.photosort.Prefs
import kotlinx.coroutines.launch

/**
 * Returns a function that asks the system to move every pending item to the trash.
 * Android shows one confirmation dialog for the whole batch. [onDone] receives the ids
 * that were trashed and their total size (empty if the user cancelled or nothing was pending).
 */
@Composable
fun rememberTrashLauncher(prefs: Prefs, onDone: (trashed: Set<Long>, bytes: Long) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var requested by remember { mutableStateOf<List<Media>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val ids = requested.mapTo(HashSet()) { it.id }
            val bytes = requested.sumOf { it.size }
            prefs.unmarkPending(ids)
            prefs.addFreed(bytes)
            onDone(ids, bytes)
        } else {
            onDone(emptySet(), 0)
        }
    }
    return {
        scope.launch {
            val existing = MediaRepository.loadByIds(context, prefs.pendingIds)
            // forget marks for items that were removed some other way
            prefs.unmarkPending(prefs.pendingIds - existing.mapTo(HashSet()) { it.id })
            if (existing.isEmpty()) {
                onDone(emptySet(), 0)
                return@launch
            }
            requested = existing
            val request = MediaStore.createTrashRequest(context.contentResolver, existing.map { it.uri }, true)
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }
}
