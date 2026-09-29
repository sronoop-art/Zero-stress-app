package com.zerostress.manager.models

import android.content.Context
import com.zerostress.manager.R
import com.zerostress.manager.ota.ZsAssetUpdater

/**
 * The 8 rank titles shown on the My Titles screen and used for avatar frames.
 *
 * The unlock thresholds match the score ladder used across the app
 * (Player.getRankTier extended upward), so a title unlocks exactly when the
 * player reaches that rank.
 *
 * PNG assets (all optional, dropped into `app/src/main/res/drawable/`):
 *  - Title badge:  ic_title_<id>.png      e.g. ic_title_bronze.png
 *  - Avatar frame: frame_<id>.png         e.g. frame_bronze.png
 *
 * If a PNG is missing the code falls back gracefully (colored medallion /
 * plain colored ring), so the build never depends on the assets.
 */
object ZsRankTitles {

    data class RankTitle(
        val id: String,
        val name: String,
        val requirement: String,
        val unlockScore: Long,
        val color: Int
    )

    val ALL: List<RankTitle> = listOf(
        RankTitle("bronze", "Bronze", "Reach 600 score", 600, 0xFFCD7F32.toInt()),
        RankTitle("silver", "Silver", "Reach 1,200 score", 1200, 0xFFC0C0C0.toInt()),
        RankTitle("gold", "Gold", "Reach 2,000 score", 2000, 0xFFFFD700.toInt()),
        RankTitle("platinum", "Platinum", "Reach 3,000 score", 3000, 0xFFE5E4E2.toInt()),
        RankTitle("diamond", "Diamond", "Reach 4,000 score", 4000, 0xFF4FC3F7.toInt()),
        RankTitle("heroic", "Heroic", "Reach 5,000 score", 5000, 0xFFB388FF.toInt()),
        RankTitle("master", "Master", "Reach 7,000 score", 7000, 0xFFFF5252.toInt()),
        RankTitle("grandmaster", "Grandmaster", "Reach 10,000 score", 10000, 0xFFFFD54F.toInt())
    )

    /** The highest title whose threshold the score has reached, or null below Bronze. */
    fun titleForScore(score: Long): RankTitle? = ALL.lastOrNull { score >= it.unlockScore }

    /** Finds a title by its Firestore display name (e.g. the equipped "title" field). */
    fun byName(name: String?): RankTitle? = ALL.firstOrNull { it.name == name }

    // ------------------------------------------------------------------
    // Bundled PNG lookups.
    //
    // IMPORTANT: these MUST be compile-time R.drawable.<name> references.
    // Release builds run with isShrinkResources = true, and the resource
    // shrinker cannot see string-based lookups (Resources.getIdentifier) -
    // with those it silently deleted every bundled title PNG from the
    // release APK while debug builds kept them, so titles showed no PNG
    // badge/frames in the shipped app. One explicit reference per rank
    // keeps all 8 PNGs in every build type. res/raw/keep.xml additionally
    // shields them (and future ic_title_* art) by wildcard.
    // ------------------------------------------------------------------

    /** Compile-time resource id of the bundled frame_<id>.png, or 0 when missing. */
    fun frameRes(title: RankTitle): Int = when (title.id) {
        "bronze" -> R.drawable.frame_bronze
        "silver" -> R.drawable.frame_silver
        "gold" -> R.drawable.frame_gold
        "platinum" -> R.drawable.frame_platinum
        "diamond" -> R.drawable.frame_diamond
        "heroic" -> R.drawable.frame_heroic
        "master" -> R.drawable.frame_master
        "grandmaster" -> R.drawable.frame_grandmaster
        else -> 0
    }

    /**
     * Drawable resource id for the title badge shown in My Titles.
     *
     * Prefers a dedicated `ic_title_<id>.png` when one has been added (found
     * by name; kept alive in release builds by res/raw/keep.xml), and falls
     * back to the rank's compile-time frame_<id>.png ring. The rings have a
     * transparent centre, so the rank initial shows through the hole and the
     * tile reads as a proper rank medallion.
     */
    fun badgeRes(context: Context, title: RankTitle): Int {
        val badge = context.resources.getIdentifier(
            "ic_title_${title.id}", "drawable", context.packageName
        )
        return if (badge != 0) badge else frameRes(title)
    }

    /** Local OTA file for `frame_<id>.png`, or null when not downloaded. */
    fun frameFile(context: Context, title: RankTitle): java.io.File? {
        val f = java.io.File(ZsAssetUpdater.otaDir(context), "frame_${title.id}.png")
        return if (f.exists() && f.length() > 0) f else null
    }

    /**
     * Frame source for the title: an OTA file when downloaded (takes priority),
     * otherwise the bundled drawable resource id, otherwise null (use the
     * fallback ring). Uses compile-time R field access so the APK resource
     * shrinker keeps frame_<id>.png even in minified release builds.
     */
    fun frameSource(context: Context, title: RankTitle): FrameSource? {
        frameFile(context, title)?.let { return FrameSource.File(it) }
        val res = frameRes(title)
        return if (res != 0) FrameSource.Resource(res) else null
    }

    sealed class FrameSource {
        class File(val file: java.io.File) : FrameSource()
        class Resource(val resId: Int) : FrameSource()
    }
}
