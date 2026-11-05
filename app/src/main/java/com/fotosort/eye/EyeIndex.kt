package com.fotosort.eye

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Eye's tags for every analyzed photo, kept in an append-only JSON-lines file so a scan can stop
 * and resume. Only tags at or above the recommended 0.5 cutoff are stored; a photo with no line
 * has not been analyzed yet, a photo with an empty tag map could not be read.
 */
class EyeIndex(context: Context) {
    private val file = File(context.filesDir, "eye_tags.jsonl")
    private val mutex = Mutex()
    private var tags: HashMap<Long, Map<String, Float>>? = null

    private val _version = MutableStateFlow(0)

    /** Bumps whenever the index changes, so screens know to rebuild their categories. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** A copy of everything analyzed so far: media id to tag confidences. */
    suspend fun snapshot(): Map<Long, Map<String, Float>> = mutex.withLock { HashMap(loaded()) }

    suspend fun add(id: Long, confidences: Map<String, Float>, notify: Boolean) = mutex.withLock {
        loaded()[id] = confidences
        withContext(Dispatchers.IO) {
            val line = JSONObject().put("id", id).put("t", JSONObject(confidences.mapValues { it.value.toDouble() }))
            file.appendText(line.toString() + "\n")
        }
        if (notify) _version.value++
    }

    fun notifyChanged() {
        _version.value++
    }

    suspend fun clear() = mutex.withLock {
        tags = HashMap()
        withContext(Dispatchers.IO) { file.delete() }
        _version.value++
    }

    private suspend fun loaded(): HashMap<Long, Map<String, Float>> {
        tags?.let { return it }
        val map = HashMap<Long, Map<String, Float>>()
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext
            file.forEachLine { line ->
                // a line cut short by the app being killed mid-write is skipped
                val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                val t = o.getJSONObject("t")
                map[o.getLong("id")] = t.keys().asSequence().associateWith { t.getDouble(it).toFloat() }
            }
        }
        tags = map
        return map
    }
}
