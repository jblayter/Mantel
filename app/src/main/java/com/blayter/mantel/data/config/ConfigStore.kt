package com.blayter.mantel.data.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** The stored config, and whether a pushed token had to be thrown away. */
data class StoredConfig(val config: MantelConfig, val tokenNotStored: Boolean)

/**
 * Keeps the config after the one-time file read, in
 * EncryptedSharedPreferences. If the keystore misbehaves on a device, the
 * address and repository names fall back to plain private preferences so the
 * display still works, but the GitHub token is never written unencrypted: it
 * is dropped, and [StoredConfig.tokenNotStored] makes the screen say so.
 */
class ConfigStore(context: Context) {
    private val appContext = context.applicationContext

    var usesEncryption: Boolean = true
        private set

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            appContext,
            "mantel_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        usesEncryption = false
        Log.w("ConfigStore", "EncryptedSharedPreferences unavailable; using plain private preferences", e)
        appContext.getSharedPreferences("mantel_prefs_fallback", Context.MODE_PRIVATE)
    }

    fun load(): StoredConfig? {
        val url = prefs.getString(KEY_GLANCES_URL, null) ?: return null
        val repos = prefs.getString(KEY_REPOS, null).orEmpty().split('\n').filter { it.isNotEmpty() }
        val token = if (usesEncryption) prefs.getString(KEY_GITHUB_TOKEN, null) else null
        val more = prefs.getString(KEY_MORE_GLANCES_URLS, null).orEmpty().split('\n').filter { it.isNotEmpty() }
        return StoredConfig(MantelConfig(url, token, repos, more), prefs.getBoolean(KEY_TOKEN_NOT_STORED, false))
    }

    /** Replaces everything stored. Returns what [load] will now give back. */
    fun save(config: MantelConfig): StoredConfig {
        val dropToken = config.githubToken != null && !usesEncryption
        val editor = prefs.edit()
            .putString(KEY_GLANCES_URL, config.glancesUrl)
            .putString(KEY_MORE_GLANCES_URLS, config.moreGlancesUrls.joinToString("\n"))
            .putString(KEY_REPOS, config.repos.joinToString("\n"))
            .putBoolean(KEY_TOKEN_NOT_STORED, dropToken)
        if (config.githubToken != null && usesEncryption) {
            editor.putString(KEY_GITHUB_TOKEN, config.githubToken)
        } else {
            editor.remove(KEY_GITHUB_TOKEN)
        }
        editor.apply()
        return StoredConfig(if (dropToken) config.copy(githubToken = null) else config, dropToken)
    }

    private companion object {
        const val KEY_GLANCES_URL = "glances_url"
        const val KEY_MORE_GLANCES_URLS = "more_glances_urls"
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_REPOS = "repos"
        const val KEY_TOKEN_NOT_STORED = "token_not_stored"
    }
}
