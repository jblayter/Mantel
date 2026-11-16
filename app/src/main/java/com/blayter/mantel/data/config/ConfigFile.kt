package com.blayter.mantel.data.config

import android.content.Context
import java.io.File

sealed interface ConfigFileResult {
    data object NotFound : ConfigFileResult
    data class Loaded(val config: MantelConfig) : ConfigFileResult
    data class Unusable(val reason: String) : ConfigFileResult
}

/**
 * The one-time setup drop: `mantel-config.json`, pushed over adb. Read once,
 * handed to [ConfigStore], then deleted. Nothing in it is ever in the APK or
 * the repository.
 *
 * The app's internal files dir is checked first, then its external one. From
 * Android 11 (the Fire TV is Android 11) adb cannot write into
 * `/sdcard/Android/data/<package>/`, so the documented route is a push to
 * `/data/local/tmp` followed by a `run-as` copy into internal storage. The
 * external dir still works with a file manager on a release build, where
 * `run-as` is unavailable. Same approach as the KrakenTV board.
 */
object ConfigFile {
    const val NAME = "mantel-config.json"

    /** The two commands the setup screen and README show. */
    const val PUSH_COMMAND = "adb push $NAME /data/local/tmp/"
    const val INSTALL_COMMAND = "adb shell run-as com.blayter.mantel cp /data/local/tmp/$NAME files/"

    private fun candidates(context: Context): List<File> = listOfNotNull(
        File(context.filesDir, NAME),
        // Asking for the external dir also creates it, so a file manager can find it.
        context.getExternalFilesDir(null)?.let { File(it, NAME) },
    )

    /**
     * Reads the file and deletes it on success. A file that cannot be used is
     * left where it is, so the setup screen can keep saying what is wrong
     * with it until a corrected one replaces it.
     */
    fun tryRead(context: Context): ConfigFileResult {
        val file = candidates(context).firstOrNull { it.exists() } ?: return ConfigFileResult.NotFound
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return ConfigFileResult.Unusable("it could not be read")
        }
        return when (val parsed = parseConfig(text)) {
            is ConfigParseResult.Ok -> {
                file.delete()
                ConfigFileResult.Loaded(parsed.config)
            }
            is ConfigParseResult.Invalid -> ConfigFileResult.Unusable(parsed.reason)
        }
    }
}
