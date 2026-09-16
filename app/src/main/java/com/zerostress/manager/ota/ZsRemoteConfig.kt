package com.zerostress.manager.ota

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.zerostress.manager.BuildConfig

/**
 * Firebase Remote Config wrapper - the app's OTA control panel.
 *
 * Values you can change in the Firebase console (Remote Config) WITHOUT
 * shipping a new APK:
 *
 * | Key                    | Default | Meaning |
 * |------------------------|---------|---------|
 * | min_version_code       | 1       | Devices below this see a blocking "Update required" dialog |
 * | update_url             | ""      | Where the download page (index.html / Drive / etc.) lives |
 * | update_message         | ...     | Text shown in the update dialog |
 * | content_pack_url       | ""      | ZIP with OTA assets (frames/badges) - see ZsAssetUpdater |
 * | content_pack_version   | 0       | Bump this to force devices to re-download the pack |
 * | title_bronze_score     | 600     | Rank-title unlock scores (8 keys, one per title) |
 * | ..._silver..grandmaster| ...     |   |
 *
 * Fetch happens at splash; cached values are used when offline.
 */
object ZsRemoteConfig {

    private const val MIN_VERSION_CODE = "min_version_code"
    private const val UPDATE_URL = "update_url"
    private const val UPDATE_MESSAGE = "update_message"
    private const val CONTENT_PACK_URL = "content_pack_url"
    private const val CONTENT_PACK_VERSION = "content_pack_version"
    private const val INTRO_VIDEO_URL = "intro_video_url"
    private const val INTRO_VIDEO_VERSION = "intro_video_version"
    private const val AUDIO_APP_START_URL = "audio_app_start_url"
    private const val AUDIO_LOGIN_URL = "audio_login_success_url"
    private const val AUDIO_REGISTER_URL = "audio_register_success_url"
    private const val PREFIX_TITLE = "title_"
    private const val SUFFIX_SCORE = "_score"

    // In-memory cache of the rank-title thresholds, refreshed on fetch.
    @Volatile
    var titleScores: Map<String, Long> = emptyMap()
        private set

    private val defaults: Map<String, Any> = buildMap {
        put(MIN_VERSION_CODE, 1L)
        put(UPDATE_URL, "")
        put(UPDATE_MESSAGE, "A new version is available. Please update to keep playing.")
        put(CONTENT_PACK_URL, "")
        put(CONTENT_PACK_VERSION, 0L)
        // Cloudinary profile-picture hosting (both blank = feature disabled)
        put("cloudinary_cloud_name", "")
        put("cloudinary_upload_preset", "")
        // Intro video (blank URL = feature disabled). Bump the version key
        // to make every device play the new video once again.
        put(INTRO_VIDEO_URL, "")
        put(INTRO_VIDEO_VERSION, 1L)
        // Remote audio (blank URL = use the bundled res/raw sound). Changing
        // a URL makes devices download the new MP3 once, then use it.
        put(AUDIO_APP_START_URL, "")
        put(AUDIO_LOGIN_URL, "")
        put(AUDIO_REGISTER_URL, "")
        // Defaults must mirror ZsRankTitles.ALL thresholds
        put("title_bronze_score", 600L)
        put("title_silver_score", 1200L)
        put("title_gold_score", 2000L)
        put("title_platinum_score", 3000L)
        put("title_diamond_score", 4000L)
        put("title_heroic_score", 5000L)
        put("title_master_score", 7000L)
        put("title_grandmaster_score", 10000L)
    }

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        val remote = FirebaseRemoteConfig.getInstance()
        remote.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1 h during active use
                .build()
        )
        remote.setDefaultsAsync(defaults)
    }

    /** Fetch + activate. Callback fires with success=true/false (never throws). */
    fun fetchAndActivate(onDone: (Boolean) -> Unit) {
        FirebaseRemoteConfig.getInstance().fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    refreshTitleScores()
                }
                onDone(task.isSuccessful)
            }
    }

    private fun refreshTitleScores() {
        val remote = FirebaseRemoteConfig.getInstance()
        titleScores = ZsRankTitleIds.ALL.associateWith { id ->
            remote.getLong("$PREFIX_TITLE$id$SUFFIX_SCORE")
        }
    }

    /** Unlock score for a rank title id, falling back to the built-in default. */
    fun titleScore(id: String, fallback: Long): Long {
        titleScores[id]?.let { return it }
        return try {
            val v = FirebaseRemoteConfig.getInstance().getLong("$PREFIX_TITLE$id$SUFFIX_SCORE")
            if (v > 0) v else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    fun minVersionCode(): Long = safeLong(MIN_VERSION_CODE, 1L)

    fun updateUrl(): String = safeString(UPDATE_URL, "")

    fun updateMessage(): String =
        safeString(UPDATE_MESSAGE, "A new version is available. Please update to keep playing.")

    fun contentPackUrl(): String = safeString(CONTENT_PACK_URL, "")

    fun contentPackVersion(): Long = safeLong(CONTENT_PACK_VERSION, 0L)

    /** Intro video URL ("" = disabled). Set in Remote Config. */
    fun introVideoUrl(): String = safeString(INTRO_VIDEO_URL, "")

    /** Bump this in the console to make devices play the new intro once. */
    fun introVideoVersion(): Long = safeLong(INTRO_VIDEO_VERSION, 1L)

    /** Direct MP3 URLs for the app sounds ("" = use bundled res/raw audio). */
    fun audioAppStartUrl(): String = safeString(AUDIO_APP_START_URL, "")
    fun audioLoginSuccessUrl(): String = safeString(AUDIO_LOGIN_URL, "")
    fun audioRegisterSuccessUrl(): String = safeString(AUDIO_REGISTER_URL, "")

    /** True when this installed build is below the console-set minimum version. */
    fun updateRequired(): Boolean =
        BuildConfig.VERSION_CODE < minVersionCode()

    private fun safeLong(key: String, fallback: Long): Long = try {
        FirebaseRemoteConfig.getInstance().getLong(key)
    } catch (_: Exception) {
        fallback
    }

    private fun safeString(key: String, fallback: String): String = try {
        FirebaseRemoteConfig.getInstance().getString(key).ifEmpty { fallback }
    } catch (_: Exception) {
        fallback
    }
}

/** Rank title ids shared between the defaults map and ZsRankTitles. */
object ZsRankTitleIds {
    val ALL = listOf(
        "bronze", "silver", "gold", "platinum",
        "diamond", "heroic", "master", "grandmaster"
    )
}
