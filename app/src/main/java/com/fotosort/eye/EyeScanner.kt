package com.fotosort.eye

import ai.desertant.eye.Eye
import ai.desertant.eye.EyeException
import ai.desertant.eye.Level
import ai.desertant.eye.Options
import ai.desertant.eye.Part
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import com.fotosort.MediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface ScanState {
    data object Idle : ScanState

    /** First run: fetching the model (about 66 MB). */
    data object Downloading : ScanState

    data class Scanning(val done: Int, val total: Int, val msPerPhoto: Long?) : ScanState

    data class Failed(val message: String) : ScanState
}

/**
 * Runs Eye over every photo not yet in the [index]. Lives in the application so a scan keeps going
 * while the user switches tabs or sorts photos; [stop] pauses it and [start] picks up where it left off.
 */
class EyeScanner(private val context: Context, private val index: EyeIndex) {
    /**
     * One low-priority thread, so analysis yields to the UI instead of competing with it on the
     * shared Default pool. Threads Eye starts natively inherit the lower priority.
     */
    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "eye-scan")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var job: Job? = null

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** Set by [stop] so [autoStart] doesn't undo a pause. */
    private var pausedByUser = false

    fun start() {
        pausedByUser = false
        if (job?.isActive == true) return
        job = scope.launch { scan(auto = false) }
    }

    /**
     * Analyzes photos added since the last scan, without asking. Does nothing if the user paused,
     * and never downloads the model on its own (it lives in the cache, which Android may clear).
     */
    fun autoStart() {
        if (pausedByUser || job?.isActive == true) return
        job = scope.launch { scan(auto = true) }
    }

    fun stop() {
        pausedByUser = true
        job?.cancel()
    }

    private suspend fun scan(auto: Boolean) {
        try {
            val all = MediaRepository.loadAllImages(context)
            val analyzed = index.snapshot().keys
            val todo = all.filter { it.id !in analyzed }
            if (todo.isEmpty()) {
                _state.value = ScanState.Idle
                return
            }
            val alreadyDone = all.size - todo.size
            Eye(context).use { eye ->
                if (!eye.isDownloaded()) {
                    if (auto) return
                    _state.value = ScanState.Downloading
                    eye.download()
                }
                val started = SystemClock.elapsedRealtime()
                var lastNotify = started
                todo.forEachIndexed { i, media ->
                    coroutineContext.ensureActive()
                    val msPerPhoto = if (i > 0) (SystemClock.elapsedRealtime() - started) / i else null
                    _state.value = ScanState.Scanning(alreadyDone + i, all.size, msPerPhoto)
                    // rebuilding the categories is not free, so refresh them every few seconds, not per photo
                    val now = SystemClock.elapsedRealtime()
                    val notify = now - lastNotify >= NOTIFY_INTERVAL_MS
                    if (notify) lastNotify = now
                    index.add(media.id, analyze(eye, media.uri), notify)
                }
            }
            index.notifyChanged()
            _state.value = ScanState.Idle
        } catch (e: CancellationException) {
            index.notifyChanged()
            _state.value = ScanState.Idle
            throw e
        } catch (e: EyeException) {
            _state.value = ScanState.Failed(e.message ?: "Analysis failed")
        } catch (e: IOException) {
            _state.value = ScanState.Failed("Couldn't download the model. Check your connection and try again.")
        } catch (e: LinkageError) {
            // the SDK ships native code for arm64-v8a and x86_64 only
            _state.value = ScanState.Failed("Photo analysis isn't supported on this phone.")
        }
    }

    /** Tags at the recommended cutoff, or an empty map when the file can't be decoded. */
    private suspend fun analyze(eye: Eye, uri: Uri): Map<String, Float> {
        val bitmap = try {
            decode(uri)
        } catch (e: IOException) {
            return emptyMap()
        }
        return try {
            val analysis = eye.analyze(bitmap, OPTIONS)
            analysis.confidences
                .filterValues { it >= Level.RECOMMENDED.cutoff }
                .entries.associate { it.key.id to it.value.toFloat() }
        } finally {
            bitmap.recycle()
        }
    }

    /** Upright (ImageDecoder applies EXIF rotation) and at most [MAX_SIDE] pixels on the long side. */
    private fun decode(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }

    private companion object {
        /** Eye works from a 384px crop and a 768px face pass, so more pixels only slow decoding down. */
        const val MAX_SIDE = 1024
        const val NOTIFY_INTERVAL_MS = 5_000L

        // Layout (boxes and crops) isn't used for categories; faces and sharpness feed eyes_closed and blurry.
        val OPTIONS = Options(parts = setOf(Part.PHOTO, Part.FACES, Part.SHARPNESS))
    }
}
