package com.fotosort

import android.content.Context
import androidx.core.content.edit

/**
 * Persists sorting decisions.
 * - kept: photos swiped left, skipped next time when [skipKept] is on
 * - pending: photos swiped right that are waiting to be moved to the system trash
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fotosort", Context.MODE_PRIVATE)
    private val kept = sp.getStringSet(KEY_KEPT, emptySet())!!.mapTo(HashSet()) { it.toLong() }
    private val pending = sp.getStringSet(KEY_PENDING, emptySet())!!.mapTo(HashSet()) { it.toLong() }

    var skipKept: Boolean
        get() = sp.getBoolean(KEY_SKIP_KEPT, true)
        set(value) = sp.edit { putBoolean(KEY_SKIP_KEPT, value) }

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

    fun unmarkPending(ids: Collection<Long>) {
        pending -= ids.toSet()
        save()
    }

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
    }
}
