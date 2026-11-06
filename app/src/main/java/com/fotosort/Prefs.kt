package com.fotosort

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /** Folders and categories as full-width rows instead of a grid of tiles. */
    var listLayout: Boolean
        get() = sp.getBoolean(KEY_LIST_LAYOUT, false)
        set(value) = sp.edit { putBoolean(KEY_LIST_LAYOUT, value) }

    /** Swipe right to keep and left to delete, instead of the default left = keep. */
    var swipeRightToKeep: Boolean
        get() = sp.getBoolean(KEY_SWIPE_RIGHT_KEEP, false)
        set(value) = sp.edit { putBoolean(KEY_SWIPE_RIGHT_KEEP, value) }

    /** Leaving a folder with items marked for trash asks to move them right away. */
    var trashOnLeave: Boolean
        get() = sp.getBoolean(KEY_TRASH_ON_LEAVE, true)
        set(value) = sp.edit { putBoolean(KEY_TRASH_ON_LEAVE, value) }

    var haptics: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(value) = sp.edit { putBoolean(KEY_HAPTICS, value) }

    private var themeState by mutableStateOf(
        sp.getString(KEY_THEME, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.Dark,
    )

    /** Observable so the whole app recomposes when it changes. */
    var theme: ThemeMode
        get() = themeState
        set(value) {
            themeState = value
            sp.edit { putString(KEY_THEME, value.name) }
        }

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
        const val KEY_LIST_LAYOUT = "list_layout"
        const val KEY_FREED = "freed_bytes"
        const val KEY_SWIPE_RIGHT_KEEP = "swipe_right_keep"
        const val KEY_TRASH_ON_LEAVE = "trash_on_leave"
        const val KEY_HAPTICS = "haptics"
        const val KEY_THEME = "theme"
    }
}

enum class ThemeMode(val label: String) {
    System("Follow system"),
    Light("Light"),
    Dark("Dark"),
}
