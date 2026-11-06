package com.photosort.ui

import android.content.Context
import android.text.format.Formatter
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

fun formatDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    } else {
        "%d:%02d".format(seconds / 60, seconds % 60)
    }
}

/** 12345 -> "12,345" in the user's locale. */
fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)
