package com.blayter.mantel.data.github

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * The GitHub App people sign in to. The client ID is public by design: the
 * device sign-in uses no client secret, and none is in the app or the
 * repository. The app asks GitHub for read access to Actions and Metadata
 * only. A fork that registers its own app changes these two lines.
 */
object GitHubApp {
    const val CLIENT_ID = "Iv23lij8TOA0z1F7U9nl"

    /** Where the owner chooses which repositories Mantel may read, as a person would type it. */
    const val PAGE = "github.com/apps/mantel-ci-cd-display"
}

/** The code GitHub hands out for one sign-in attempt. */
data class DeviceCode(
    /** Sent back when asking whether the person has approved. Never shown. */
    val deviceCode: String,
    /** What the person types on GitHub: `WDJB-MJHT`. */
    val userCode: String,
    /** Where they type it: `https://github.com/login/device`. */
    val verificationUri: String,
    val expiresInSeconds: Long,
    /** The shortest wait GitHub allows between two asks. */
    val intervalSeconds: Long,
) {
    override fun toString() = "DeviceCode(userCode=$userCode)"
}

/**
 * What signing in gives: a token that can read, and, unless the app has
 * expiry switched off, a refresh token that replaces both when the first runs
 * out. Never log or display either.
 */
data class GitHubToken(
    val accessToken: String,
    val accessExpiresAt: Instant? = null,
    val refreshToken: String? = null,
    val refreshExpiresAt: Instant? = null,
) {
    override fun toString() = "GitHubToken(expires=$accessExpiresAt, refresh=${if (refreshToken == null) "none" else "set"})"
}

/** What GitHub said when asked whether the code has been approved. */
sealed interface PollAnswer {
    data class Approved(val token: GitHubToken) : PollAnswer

    /** Not yet; ask again after the interval. */
    data object Pending : PollAnswer

    /** Asked too often; wait this long between asks from now on. */
    data class SlowDown(val intervalSeconds: Long?) : PollAnswer

    /** The code ran out before it was approved. */
    data object Expired : PollAnswer

    /** The person pressed Cancel on GitHub. */
    data object Denied : PollAnswer

    /** Anything else: the app is set up wrongly, or GitHub changed. [code] is GitHub's word for it. */
    data class Failed(val code: String) : PollAnswer
}

/** A repository the signed-in person lets Mantel read. */
data class ReadableRepo(val fullName: String, val pushedAt: Instant?)

/** GitHub refused the refresh token: the person has to sign in again. */
class RefreshRejectedException(code: String) : Exception("GitHub refused the refresh token: $code")

/** Pure reading of GitHub's sign-in answers, tested without a network. */
object GitHubAuth {
    /** The deploys card asks every repository each poll, so it watches only this many: the most recently pushed. */
    const val MAX_REPOSITORIES = 12

    private val json = Json { ignoreUnknownKeys = true }

    private fun obj(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    /** The answer to `POST /login/device/code`. Throws if it is not a code. */
    fun parseDeviceCode(text: String): DeviceCode {
        val o = obj(text)
        return DeviceCode(
            deviceCode = requireNotNull(o.text("device_code")) { "no device_code: ${o.text("error")}" },
            userCode = requireNotNull(o.text("user_code")) { "no user_code" },
            verificationUri = o.text("verification_uri") ?: "https://github.com/login/device",
            expiresInSeconds = o.number("expires_in") ?: 900,
            intervalSeconds = o.number("interval") ?: 5,
        )
    }

    private fun token(o: JsonObject, now: Instant): GitHubToken? {
        val access = o.text("access_token") ?: return null
        return GitHubToken(
            accessToken = access,
            accessExpiresAt = o.number("expires_in")?.let(now::plusSeconds),
            refreshToken = o.text("refresh_token"),
            refreshExpiresAt = o.number("refresh_token_expires_in")?.let(now::plusSeconds),
        )
    }

    /** The answer to `POST /login/oauth/access_token` while waiting for approval. */
    fun parsePoll(text: String, now: Instant): PollAnswer {
        val o = obj(text)
        token(o, now)?.let { return PollAnswer.Approved(it) }
        return when (val error = o.text("error") ?: "no_answer") {
            "authorization_pending" -> PollAnswer.Pending
            "slow_down" -> PollAnswer.SlowDown((o["interval"] as? JsonPrimitive)?.intOrNull?.toLong())
            // GitHub's table says expired_token and its text says token_expired.
            "expired_token", "token_expired" -> PollAnswer.Expired
            "access_denied" -> PollAnswer.Denied
            else -> PollAnswer.Failed(error)
        }
    }

    /** The answer to a refresh. Throws [RefreshRejectedException] when GitHub names an error. */
    fun parseRefresh(text: String, now: Instant): GitHubToken {
        val o = obj(text)
        return token(o, now) ?: throw RefreshRejectedException(o.text("error") ?: "no_answer")
    }

    /** `GET /user`: the name the person signs in with. */
    fun parseLogin(text: String): String = requireNotNull(obj(text).text("login")) { "no login" }

    /** `GET /user/installations`: where the person has installed the app. */
    fun parseInstallationIds(text: String): List<Long> =
        (obj(text)["installations"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.number("id") }

    /** `GET /user/installations/{id}/repositories`. */
    fun parseRepositories(text: String): List<ReadableRepo> =
        (obj(text)["repositories"] as? JsonArray).orEmpty().mapNotNull { element ->
            val repo = element as? JsonObject ?: return@mapNotNull null
            val name = repo.text("full_name") ?: return@mapNotNull null
            ReadableRepo(name, repo.text("pushed_at")?.let { runCatching { Instant.parse(it) }.getOrNull() })
        }

    /**
     * The repositories the deploys card watches, as `owner/name` in name
     * order: all of them, or the [MAX_REPOSITORIES] most recently pushed.
     */
    fun watched(all: List<ReadableRepo>): List<String> =
        all.distinctBy { it.fullName.lowercase() }
            .sortedByDescending { it.pushedAt ?: Instant.EPOCH }
            .take(MAX_REPOSITORIES)
            .map { it.fullName }
            .sortedBy { it.lowercase() }
}

/** A finished sign-in: who it is and what they let Mantel read, as `owner/name`. */
data class GitHubAccount(val token: GitHubToken, val login: String, val repos: List<String>)
