package com.zerostress.manager

import android.content.Context

/**
 * Persists the player-dashboard layout (section order + hidden set) in
 * SharedPreferences as simple JSON. The dashboard renders its sections from
 * [loadOrder] each composition, so layout edits apply instantly without an
 * APK rebuild.
 */
object ZsDashboardLayout {

    const val DEFAULT_ORDER = "identity,score,performance,recentForm,leaderboard,nextMatch"

    private const val PREFS = "zs_dashboard_layout"
    private const val KEY_ORDER = "order"
    private const val KEY_HIDDEN = "hidden"
    private const val KEY_UPDATED = "updatedMs"

    /** All sections the dashboard knows, in canonical order. */
    val ALL_SECTIONS: List<String> = DEFAULT_ORDER.split(",")

    fun loadOrder(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ORDER, DEFAULT_ORDER) ?: DEFAULT_ORDER
        val stored = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val known = stored.filter { it in ALL_SECTIONS }.distinct()
        // Append any sections missing from stored order (new sections added
        // by app updates keep working without migration).
        return known + ALL_SECTIONS.filter { it !in known }
    }

    fun loadHidden(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (prefs.getString(KEY_HIDDEN, "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun save(context: Context, order: List<String>, hidden: Set<String>) {
        val safeOrder = order.filter { it in ALL_SECTIONS }.distinct() +
            ALL_SECTIONS.filter { it !in order }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, safeOrder.joinToString(","))
            .putString(KEY_HIDDEN, hidden.filter { it in ALL_SECTIONS }.joinToString(","))
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
