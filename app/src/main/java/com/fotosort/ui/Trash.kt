package com.fotosort.ui

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
import com.fotosort.MediaRepository
import com.fotosort.Prefs
import kotlinx.coroutines.launch

/**
 * Returns a function that asks the system to move every pending photo to the trash.
 * Android shows one confirmation dialog for the whole batch. [onDone] receives the ids
 * that were trashed (empty if the user cancelled or nothing was pending).
 */
@Composable
fun rememberTrashLauncher(prefs: Prefs, onDone: (trashed: Set<Long>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var requested by remember { mutableStateOf<List<Long>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            prefs.unmarkPending(requested)
            onDone(requested.toSet())
        } else {
            onDone(emptySet())
        }
    }
    return {
        scope.launch {
            val existing = MediaRepository.existingIds(context, prefs.pendingIds)
            // forget marks for photos that were removed some other way
            prefs.unmarkPending(prefs.pendingIds - existing.toSet())
            if (existing.isEmpty()) {
                onDone(emptySet())
                return@launch
            }
            requested = existing
            val request = MediaStore.createTrashRequest(
                context.contentResolver,
                existing.map(MediaRepository::uriFor),
                true,
            )
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }
}
