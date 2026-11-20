package com.blayter.mantel.data.github

import com.blayter.mantel.data.DeployFailure
import com.blayter.mantel.data.DeploySourceException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant

class GitHubAuthTest {
    private val now = Instant.parse("2026-11-20T12:00:00Z")

    private val tokenReply =
        """{"access_token":"ghu_a","expires_in":28800,"refresh_token":"ghr_r","refresh_token_expires_in":15897600,"scope":"","token_type":"bearer"}"""

    @Test
    fun readsTheDeviceCode() {
        val code = GitHubAuth.parseDeviceCode(
            """{"device_code":"3584d83530557fdd1f46af8289938c8ef79f9dc5","user_code":"WDJB-MJHT","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
        )
        assertEquals("WDJB-MJHT", code.userCode)
        assertEquals("https://github.com/login/device", code.verificationUri)
        assertEquals(900, code.expiresInSeconds)
        assertEquals(5, code.intervalSeconds)
        assertFalse("the device code stays out of logs", "3584d8" in code.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun anErrorInsteadOfACodeThrows() {
        GitHubAuth.parseDeviceCode("""{"error":"device_flow_disabled"}""")
    }

    @Test
    fun readsEveryPollAnswer() {
        fun poll(error: String) = GitHubAuth.parsePoll("""{"error":"$error"}""", now)
        assertEquals(PollAnswer.Pending, poll("authorization_pending"))
        assertEquals(PollAnswer.Expired, poll("expired_token"))
        assertEquals(PollAnswer.Expired, poll("token_expired"))
        assertEquals(PollAnswer.Denied, poll("access_denied"))
        assertEquals(PollAnswer.Failed("device_flow_disabled"), poll("device_flow_disabled"))
        assertEquals(PollAnswer.SlowDown(10), GitHubAuth.parsePoll("""{"error":"slow_down","interval":10}""", now))
        assertEquals(PollAnswer.SlowDown(null), poll("slow_down"))

        val token = (GitHubAuth.parsePoll(tokenReply, now) as PollAnswer.Approved).token
        assertEquals("ghu_a", token.accessToken)
        assertEquals(now.plusSeconds(28800), token.accessExpiresAt)
        assertEquals("ghr_r", token.refreshToken)
        assertFalse("tokens stay out of logs", "ghu_a" in token.toString() || "ghr_r" in token.toString())
    }

    @Test
    fun aTokenThatNeverExpiresHasNoRefreshToken() {
        val token = (GitHubAuth.parsePoll("""{"access_token":"ghu_a","scope":"","token_type":"bearer"}""", now) as PollAnswer.Approved).token
        assertNull(token.accessExpiresAt)
        assertNull(token.refreshToken)
    }

    @Test
    fun readsWhoSignedInAndWhatTheyChose() {
        assertEquals("octocat", GitHubAuth.parseLogin("""{"login":"octocat","id":1}"""))
        assertEquals(listOf(7L, 9L), GitHubAuth.parseInstallationIds("""{"total_count":2,"installations":[{"id":7},{"id":9}]}"""))
        assertEquals(emptyList<Long>(), GitHubAuth.parseInstallationIds("""{"total_count":0,"installations":[]}"""))
        val repos = GitHubAuth.parseRepositories(
            """{"total_count":2,"repositories":[{"full_name":"octocat/storefront","pushed_at":"2026-11-19T08:00:00Z"},{"full_name":"octocat/api"}]}""",
        )
        assertEquals(listOf("octocat/storefront", "octocat/api"), repos.map { it.fullName })
        assertEquals(Instant.parse("2026-11-19T08:00:00Z"), repos[0].pushedAt)
    }

    @Test
    fun watchesTheMostRecentlyPushedInNameOrder() {
        val many = (1..20).map { ReadableRepo("o/r%02d".format(it), now.minusSeconds(it * 3600L)) }
        val watched = GitHubAuth.watched(many + ReadableRepo("o/Zebra", now) + ReadableRepo("o/zebra", now))
        assertEquals(GitHubAuth.MAX_REPOSITORIES, watched.size)
        assertEquals("o/r01", watched.first())
        assertEquals("o/Zebra", watched.last())
        assertFalse("o/r12" in watched)
        assertEquals(listOf("a/b", "c/d"), GitHubAuth.watched(listOf(ReadableRepo("c/d", null), ReadableRepo("a/b", null))))
    }

    @Test
    fun signInOnlyTalksToGitHubAndSendsNoSecret() = runTest {
        val posted = mutableListOf<Pair<String, Map<String, String>>>()
        val asked = mutableListOf<String>()
        val client = GitHubSignInClient(
            clientId = "Iv_test",
            now = { now },
            post = { url, form ->
                posted += url to form
                if (url.endsWith("/login/device/code")) {
                    """{"device_code":"dc","user_code":"AAAA-BBBB","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
                } else {
                    tokenReply
                }
            },
            get = { url, headers ->
                assertEquals("Bearer ghu_a", headers["Authorization"])
                asked += url
                when {
                    url.endsWith("/user") -> """{"login":"octocat"}"""
                    "/repositories" in url -> """{"repositories":[{"full_name":"octocat/api"}]}"""
                    else -> """{"installations":[{"id":7}]}"""
                }
            },
        )
        val code = client.requestCode()
        val token = (client.poll(code) as PollAnswer.Approved).token
        client.refresh(token.refreshToken!!)
        assertEquals("octocat", client.login(token.accessToken))
        assertEquals(listOf("octocat/api"), client.repositories(token.accessToken))

        assertTrue(posted.all { it.first.startsWith("https://github.com/login/") })
        assertTrue(posted.none { "client_secret" in it.second })
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", posted[1].second["grant_type"])
        assertEquals("refresh_token", posted[2].second["grant_type"])
        assertTrue(asked.all { it.startsWith("https://api.github.com/user") })
    }

    @Test
    fun theSessionRefreshesShortlyBeforeTheTokenRunsOut() = runTest {
        var clock = now
        var refreshes = 0
        val stored = mutableListOf<GitHubToken>()
        val client = GitHubSignInClient(now = { clock }, post = { _, form ->
            refreshes++
            assertEquals("ghr_${refreshes - 1}", form["refresh_token"])
            """{"access_token":"ghu_$refreshes","expires_in":28800,"refresh_token":"ghr_$refreshes","refresh_token_expires_in":15897600}"""
        })
        val session = GitHubSession(GitHubToken("ghu_0", now.plusSeconds(28800), "ghr_0", now.plusSeconds(15897600)), client, { clock }) { stored += it }

        assertEquals("ghu_0", session.token())
        clock = now.plusSeconds(28800 - 300)
        assertEquals("ghu_1", session.token())
        assertEquals("ghu_1", session.token())
        assertEquals(1, refreshes)
        assertEquals("ghr_1", stored.single().refreshToken)
    }

    @Test
    fun aRefusedRefreshMeansSigningInAgainAndANetworkFailureDoesNot() = runTest {
        var clock = now
        var reply: () -> String = { throw IOException("offline") }
        val client = GitHubSignInClient(now = { clock }, post = { _, _ -> reply() })
        val session = GitHubSession(GitHubToken("ghu_0", now.plusSeconds(28800), "ghr_0"), client, { clock })

        clock = now.plusSeconds(28800 - 60)
        assertEquals("offline, but the old token still has a minute", "ghu_0", session.token())
        clock = now.plusSeconds(28800 + 60)
        try {
            session.token()
            fail("expected the network failure")
        } catch (e: IOException) {
            // Worth retrying: the deploys card keeps what it had.
        }
        reply = { """{"error":"bad_refresh_token"}""" }
        try {
            session.token()
            fail("expected a token failure")
        } catch (e: DeploySourceException) {
            assertEquals(DeployFailure.TokenRejected, e.failure)
        }
    }

    @Test
    fun aTokenThatNeverExpiresIsNeverRefreshed() = runTest {
        val client = GitHubSignInClient(post = { _, _ -> fail("no request expected"); "" })
        assertEquals("ghu_0", GitHubSession(GitHubToken("ghu_0"), client).token())
    }
}
