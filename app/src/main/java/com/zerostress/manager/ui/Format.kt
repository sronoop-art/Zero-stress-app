package com.zerostress.manager.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

fun formatDateTime(ts: Long): String =
    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(ts))

fun formatDate(ts: Long): String =
    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ts))

fun formatShortDateTime(ts: Long): String =
    SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(ts))

/** "Xd ago"-style relative time, or a date when older than a week. */
fun timeAgo(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        minutes < 10080 -> "${minutes / 1440}d ago"
        else -> formatDate(ts)
    }
}