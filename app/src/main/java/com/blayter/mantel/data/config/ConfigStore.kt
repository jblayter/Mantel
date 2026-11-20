package com.blayter.mantel.data.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.blayter.mantel.data.github.GitHubToken
import java.time.Instant

/** Who signed in to GitHub on the TV, and the token that came of it. */
data class StoredSignIn(val token: GitHubToken, val login: String)

/**
 * The stored config, whether a token had to be thrown away, and the GitHub
 * sign-in when setup was done on the TV.
 */
data class StoredConfig(val config: MantelConfig, val tokenNotStored: Boolean, val signIn: StoredSignIn? = null)

/**
 * Keeps the config after the one-time file read, in
 * EncryptedSharedPreferences. If the keystore misbehaves on a device, the
 * address and repository names fall back to plain private preferences so the
 * display still works, but a GitHub token is never written unencrypted: it
 * is dropped, and [StoredConfig.tokenNotStored] makes the screen say so. A
 * sign-in made on such a TV lasts until the app next starts.
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
        val access = if (usesEncryption) prefs.getString(KEY_SIGN_IN_ACCESS, null) else null
        val signIn = access?.let {
            StoredSignIn(
                GitHubToken(
                    accessToken = it,
                    accessExpiresAt = instant(KEY_SIGN_IN_ACCESS_EXPIRES),
                    refreshToken = prefs.getString(KEY_SIGN_IN_REFRESH, null),
                    refreshExpiresAt = instant(KEY_SIGN_IN_REFRESH_EXPIRES),
                ),
                prefs.getString(KEY_SIGN_IN_LOGIN, null).orEmpty(),
            )
        }
        return StoredConfig(MantelConfig(url, token, repos, more), prefs.getBoolean(KEY_TOKEN_NOT_STORED, false), signIn)
    }

    private fun instant(key: String): Instant? =
        prefs.getLong(key, 0).takeIf { it > 0 }?.let(Instant::ofEpochSecond)

    private fun SharedPreferences.Editor.putToken(token: GitHubToken) = this
        .putString(KEY_SIGN_IN_ACCESS, token.accessToken)
        .putLong(KEY_SIGN_IN_ACCESS_EXPIRES, token.accessExpiresAt?.epochSecond ?: 0)
        .putString(KEY_SIGN_IN_REFRESH, token.refreshToken)
        .putLong(KEY_SIGN_IN_REFRESH_EXPIRES, token.refreshExpiresAt?.epochSecond ?: 0)

    /** Keeps the pair a refresh gave. Does nothing on a TV that cannot encrypt. */
    fun saveToken(token: GitHubToken) {
        if (usesEncryption) prefs.edit().putToken(token).apply()
    }

    /** Keeps the list of repositories after it changed on GitHub. */
    fun saveRepos(repos: List<String>) {
        prefs.edit().putString(KEY_REPOS, repos.joinToString("\n")).apply()
    }

    /**
     * Replaces everything stored. Returns what the display should run on now:
     * the same as [load] will give back, except that a sign-in that could not
     * be encrypted is still returned, to be used until the app next starts.
     */
    fun save(config: MantelConfig, signIn: StoredSignIn? = null): StoredConfig {
        val dropToken = (config.githubToken != null || signIn != null) && !usesEncryption
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
        if (signIn != null && usesEncryption) {
            editor.putToken(signIn.token).putString(KEY_SIGN_IN_LOGIN, signIn.login)
        } else {
            for (key in SignInKeys) editor.remove(key)
        }
        editor.apply()
        return StoredConfig(if (dropToken) config.copy(githubToken = null) else config, dropToken && signIn == null, signIn)
    }

    /** Forgets everything, so the display starts setup again. Nothing changes anywhere else. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_GLANCES_URL = "glances_url"
        const val KEY_MORE_GLANCES_URLS = "more_glances_urls"
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_REPOS = "repos"
        const val KEY_TOKEN_NOT_STORED = "token_not_stored"
        const val KEY_SIGN_IN_ACCESS = "sign_in_access_token"
        const val KEY_SIGN_IN_ACCESS_EXPIRES = "sign_in_access_expires"
        const val KEY_SIGN_IN_REFRESH = "sign_in_refresh_token"
        const val KEY_SIGN_IN_REFRESH_EXPIRES = "sign_in_refresh_expires"
        const val KEY_SIGN_IN_LOGIN = "sign_in_login"
        val SignInKeys = listOf(
            KEY_SIGN_IN_ACCESS, KEY_SIGN_IN_ACCESS_EXPIRES, KEY_SIGN_IN_REFRESH, KEY_SIGN_IN_REFRESH_EXPIRES, KEY_SIGN_IN_LOGIN,
        )
    }
}
