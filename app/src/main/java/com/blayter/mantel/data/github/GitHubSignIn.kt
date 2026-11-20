package com.blayter.mantel.data.github

import com.blayter.mantel.data.DeployFailure
import com.blayter.mantel.data.DeploySourceException
import com.blayter.mantel.data.httpGet
import com.blayter.mantel.data.httpPostForm
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * Signing in to GitHub from the TV with a code typed on a phone (GitHub's
 * "device flow"), and reading who signed in and what they let Mantel read.
 *
 * The three sign-in requests are POSTs to github.com, the only ones Mantel
 * makes; they change nothing in any repository or workflow. Everything else
 * is a GET.
 *
 * @param post sends a form and returns the answer as text.
 * @param get fetches one URL as text with the given headers.
 */
class GitHubSignInClient(
    private val clientId: String = GitHubApp.CLIENT_ID,
    private val now: () -> Instant = Instant::now,
    private val post: suspend (url: String, form: Map<String, String>) -> String =
        { url, form -> httpPostForm(url, form, mapOf("Accept" to "application/json")) },
    private val get: suspend (url: String, headers: Map<String, String>) -> String = ::httpGet,
) {
    /** Asks GitHub for a code to show. Throws when GitHub cannot be reached or will not give one. */
    suspend fun requestCode(): DeviceCode =
        GitHubAuth.parseDeviceCode(post("$SITE/login/device/code", mapOf("client_id" to clientId)))

    /** Asks whether the code has been approved. Throws only when GitHub cannot be reached. */
    suspend fun poll(code: DeviceCode): PollAnswer = GitHubAuth.parsePoll(
        post(
            "$SITE/login/oauth/access_token",
            mapOf(
                "client_id" to clientId,
                "device_code" to code.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            ),
        ),
        now(),
    )

    /**
     * Swaps a refresh token for a new pair; the old pair stops working.
     * Throws [RefreshRejectedException] when GitHub refuses it.
     */
    suspend fun refresh(refreshToken: String): GitHubToken = GitHubAuth.parseRefresh(
        post(
            "$SITE/login/oauth/access_token",
            mapOf("client_id" to clientId, "grant_type" to "refresh_token", "refresh_token" to refreshToken),
        ),
        now(),
    )

    private fun headers(accessToken: String) = mapOf(
        "Authorization" to "Bearer $accessToken",
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28",
    )

    /** The name the person signed in with. */
    suspend fun login(accessToken: String): String = GitHubAuth.parseLogin(get("$API/user", headers(accessToken)))

    /**
     * The repositories to watch, as `owner/name`: the ones the person chose
     * for Mantel on GitHub. Empty until they install the app somewhere.
     */
    suspend fun repositories(accessToken: String): List<String> {
        val headers = headers(accessToken)
        val installations = GitHubAuth.parseInstallationIds(get("$API/user/installations?per_page=$PAGE_SIZE", headers))
        val all = installations.flatMap { id ->
            val found = ArrayList<ReadableRepo>()
            for (page in 1..MAX_PAGES) {
                val batch = GitHubAuth.parseRepositories(
                    get("$API/user/installations/$id/repositories?per_page=$PAGE_SIZE&page=$page", headers),
                )
                found += batch
                if (batch.size < PAGE_SIZE) break
            }
            found
        }
        return GitHubAuth.watched(all)
    }

    private companion object {
        const val SITE = "https://github.com"
        const val API = "https://api.github.com"
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 5
    }
}

/**
 * The token of a signed-in display, kept fresh. GitHub's tokens last 8 hours;
 * shortly before one runs out it is swapped for a new one, and [onChange] is
 * handed the new pair to store.
 */
class GitHubSession(
    initial: GitHubToken,
    private val client: GitHubSignInClient,
    private val now: () -> Instant = Instant::now,
    private val onChange: suspend (GitHubToken) -> Unit = {},
) {
    private val lock = Mutex()

    /** The pair in use now. */
    @Volatile
    var current: GitHubToken = initial
        private set

    /**
     * A token that works now. Throws [DeploySourceException] when the person
     * has to sign in again, anything else for a failure worth retrying.
     */
    suspend fun token(): String = lock.withLock {
        val held = current
        val expiresAt = held.accessExpiresAt ?: return@withLock held.accessToken
        val t = now()
        if (t.isBefore(expiresAt.minus(REFRESH_BEFORE))) return@withLock held.accessToken
        val refreshToken = held.refreshToken ?: throw DeploySourceException(DeployFailure.TokenRejected)
        val fresh = try {
            client.refresh(refreshToken)
        } catch (e: RefreshRejectedException) {
            throw DeploySourceException(DeployFailure.TokenRejected)
        } catch (e: Exception) {
            // GitHub could not be reached. The old token still works until it runs out.
            if (t.isBefore(expiresAt)) return@withLock held.accessToken
            throw e
        }
        current = fresh
        onChange(fresh)
        fresh.accessToken
    }

    private companion object {
        val REFRESH_BEFORE: Duration = Duration.ofMinutes(10)
    }
}
