package com.zerostress.manager.ota

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads and installs the OTA asset pack.
 *
 * The pack is a ZIP containing files named exactly like the built-in drawable
 * slots it overrides:
 *
 *   frame_bronze.png, frame_silver.png, ... frame_grandmaster.png
 *   ic_title_bronze.png, ic_title_silver.png, ... ic_title_grandmaster.png
 *
 * Files land in context.filesDir/ota and are rendered by ZsAvatarFrame /
 * ZsRankTitles ahead of the bundled drawable resources. Host the ZIP anywhere
 * a plain GET works (Firebase Storage download URL, GitHub release asset,
 * your own server, Drive direct-download link).
 *
 * To push new art to every user:
 *   1. zip the PNGs
 *   2. upload it
 *   3. set content_pack_url + bump content_pack_version in Remote Config
 *   4. devices download on next splash (or when the pack is requested)
 */
object ZsAssetUpdater {

    private const val TAG = "ZsAssetUpdater"
    private const val DIR_NAME = "ota"
    private const val VERSION_FILE = "pack_version.txt"

    /** Local directory holding the installed OTA files. */
    fun otaDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Currently installed pack version (0 = none). */
    fun installedVersion(context: Context): Long = try {
        File(otaDir(context), VERSION_FILE).readText().trim().toLongOrNull() ?: 0L
    } catch (_: Exception) {
        0L
    }

    /** True when the console pack version is newer than what is installed. */
    fun updateAvailable(context: Context): Boolean {
        val remoteVer = ZsRemoteConfig.contentPackVersion()
        return remoteVer > 0 && remoteVer > installedVersion(context)
    }

    /**
     * Downloads content_pack_url and unzips it into otaDir.
     * Runs on the calling thread - invoke from a background dispatcher.
     * Never throws; failures return false.
     */
    fun downloadIfNewer(context: Context): Boolean {
        val url = ZsRemoteConfig.contentPackUrl()
        val remoteVer = ZsRemoteConfig.contentPackVersion()
        if (url.isBlank() || remoteVer <= 0) return false
        if (remoteVer <= installedVersion(context)) return false // already current

        return try {
            val dir = otaDir(context)
            val tmpZip = File.createTempFile("zs_pack", ".zip", context.cacheDir)

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "pack download failed: HTTP ${conn.responseCode}")
                conn.disconnect()
                return false
            }

            conn.inputStream.use { input ->
                FileOutputStream(tmpZip).use { out -> input.copyTo(out) }
            }
            conn.disconnect()

            // Unzip (zip-slip safe: paths are sanitized against dir)
            ZipInputStream(FileInputStream(tmpZip)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = File(entry.name).name // strip any directory parts
                        if (name.endsWith(".png")) {
                            val out = File(dir, name)
                            FileOutputStream(out).use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            tmpZip.delete()

            File(dir, VERSION_FILE).writeText(remoteVer.toString())
            Log.i(TAG, "asset pack v$remoteVer installed (${dir.listFiles()?.size ?: 0} files)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "asset pack update failed: ${e.message}")
            false
        }
    }
}
