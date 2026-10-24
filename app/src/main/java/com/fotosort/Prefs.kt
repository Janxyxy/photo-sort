package com.fotosort

import android.content.Context
import androidx.core.content.edit

/**
 * Persists sorting decisions and settings.
 * - kept: items swiped left, skipped next time when [skipKept] is on
 * - pending: items swiped right that are waiting to be moved to the system trash
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fotosort", Context.MODE_PRIVATE)
    private val kept = sp.getStringSet(KEY_KEPT, emptySet())!!.mapTo(HashSet()) { it.toLong() }
    private val pending = sp.getStringSet(KEY_PENDING, emptySet())!!.mapTo(HashSet()) { it.toLong() }

    var skipKept: Boolean
        get() = sp.getBoolean(KEY_SKIP_KEPT, true)
        set(value) = sp.edit { putBoolean(KEY_SKIP_KEPT, value) }

    var includeVideos: Boolean
        get() = sp.getBoolean(KEY_INCLUDE_VIDEOS, true)
        set(value) = sp.edit { putBoolean(KEY_INCLUDE_VIDEOS, value) }

    var groupSimilar: Boolean
        get() = sp.getBoolean(KEY_GROUP_SIMILAR, true)
        set(value) = sp.edit { putBoolean(KEY_GROUP_SIMILAR, value) }

    var sortOrder: SortOrder
        get() = sp.getString(KEY_SORT, null)
            ?.let { name -> SortOrder.entries.firstOrNull { it.name == name } }
            ?: SortOrder.NewestFirst
        set(value) = sp.edit { putString(KEY_SORT, value.name) }

    /** Total bytes moved to the trash through the app. */
    val freedBytes: Long get() = sp.getLong(KEY_FREED, 0)

    val keptIds: Set<Long> get() = kept
    val pendingIds: Set<Long> get() = pending

    fun markKept(id: Long) {
        kept += id
        pending -= id
        save()
    }

    fun markForTrash(id: Long) {
        pending += id
        kept -= id
        save()
    }

    /** Puts an item back into the state it had before a decision (used by undo). */
    fun restore(id: Long, wasKept: Boolean, wasPending: Boolean) {
        if (wasKept) kept += id else kept -= id
        if (wasPending) pending += id else pending -= id
        save()
    }

    fun unmarkPending(ids: Collection<Long>) {
        pending -= ids.toSet()
        save()
    }

    fun addFreed(bytes: Long) = sp.edit { putLong(KEY_FREED, freedBytes + bytes) }

    fun clearKept() {
        kept.clear()
        save()
    }

    private fun save() = sp.edit {
        putStringSet(KEY_KEPT, kept.mapTo(HashSet()) { it.toString() })
        putStringSet(KEY_PENDING, pending.mapTo(HashSet()) { it.toString() })
    }

    private companion object {
        const val KEY_KEPT = "kept"
        const val KEY_PENDING = "pending"
        const val KEY_SKIP_KEPT = "skip_kept"
        const val KEY_INCLUDE_VIDEOS = "include_videos"
        const val KEY_GROUP_SIMILAR = "group_similar"
        const val KEY_SORT = "sort_order"
        const val KEY_FREED = "freed_bytes"
    }
}
