package com.blayter.mantel.data.github

import com.blayter.mantel.data.DeployFailure
import com.blayter.mantel.data.DeploySourceException
import com.blayter.mantel.data.HttpStatusException
import com.blayter.mantel.domain.DeployOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class GitHubRunsTest {
    private val now = Instant.parse("2026-11-15T20:00:00Z")

    private fun run(
        id: Long, name: String, workflowId: Long, status: String, conclusion: String?, started: String, updated: String,
        commit: String = "Fix the thing\n\nLong body", title: String = name,
    ) = """{"id":$id,"name":"$name","workflow_id":$workflowId,"status":"$status",
        "conclusion":${conclusion?.let { "\"$it\"" }},"head_branch":"main","display_title":"$title",
        "run_started_at":"$started","updated_at":"$updated",
        "head_commit":{"message":${kotlinx.serialization.json.JsonPrimitive(commit)}}}"""

    private val reply = """{"total_count":7,"workflow_runs":[
        ${run(7, "Deploy", 2, "in_progress", null, "2026-11-15T19:58:00Z", "2026-11-15T19:58:30Z")},
        ${run(6, "Release notes", 3, "completed", "success", "2026-11-15T19:14:31Z", "2026-11-15T19:14:59Z")},
        ${run(5, "Deploy", 2, "completed", "skipped", "2026-11-15T17:15:08Z", "2026-11-15T17:15:10Z")},
        ${run(4, "Deploy", 2, "completed", "success", "2026-11-15T17:10:00Z", "2026-11-15T17:20:00Z")},
        ${run(3, "Deploy", 2, "completed", "success", "2026-11-15T16:00:00Z", "2026-11-15T16:04:00Z")},
        ${run(2, "Nightly Integration", 4, "completed", "failure", "2026-11-15T02:09:24Z", "2026-11-15T02:28:58Z")},
        ${run(1, "CI", 1, "completed", "success", "2026-11-01T10:00:00Z", "2026-11-01T10:09:00Z")}
    ]}"""

    private val runs = GitHubRuns.parseRuns("example-org/RxFlow", reply)

    @Test
    fun parsesRuns() {
        assertEquals(7, runs.size)
        assertEquals("Fix the thing", runs.first().commitTitle)
        assertEquals("main", runs.first().branch)
        assertTrue(runs.first().isRunning)
        assertTrue(GitHubRuns.parseRuns("a/b", """{"workflow_runs":[]}""").isEmpty())
    }

    @Test
    fun showsRunningFirstThenNewestAndHidesSkipped() {
        assertEquals(listOf(7L, 6L, 4L, 3L), GitHubRuns.shown(runs, now).map { it.id })
    }

    @Test
    fun keepsEveryRunOfTheLastHourAndFourPerRepository() {
        fun finished(id: Long, repo: String, minutesAgo: Long) = WorkflowRun(
            id, repo, 1, "CI", "Fix", "main", "completed", "success",
            startedAt = now.minusSeconds(minutesAgo * 60 + 120), updatedAt = now.minusSeconds(minutesAgo * 60),
        )
        val busy = (1L..6L).map { finished(it, "a/busy", minutesAgo = it * 5) }
        val quiet = (11L..16L).map { finished(it, "a/quiet", minutesAgo = it * 60) }
        val shown = GitHubRuns.shown(quiet.take(3) + busy + quiet.drop(3), now)
        assertEquals("repositories in the order asked for, newest first", listOf(11L, 12L, 13L, 14L, 1L, 2L, 3L, 4L, 5L, 6L), shown.map { it.id })

        val flood = (1L..20L).map { finished(it, "a/busy", minutesAgo = it) }
        assertEquals(GitHubRuns.MAX_PER_REPOSITORY, GitHubRuns.shown(flood, now).size)
    }

    @Test
    fun dropsRunsOlderThanSevenDays() {
        val later = Instant.parse("2026-11-22T03:00:00Z")
        val finished = runs.filter { !it.isRunning }
        assertTrue("the nightly failure is 7 days and half an hour old", GitHubRuns.shown(finished, later).none { it.id == 2L })
        assertTrue(GitHubRuns.shown(finished, later).none { it.id == 1L })
        assertEquals(listOf(6L, 4L, 3L), GitHubRuns.shown(finished, later).map { it.id })
    }

    @Test
    fun estimatesProgressFromHowLongTheWorkflowUsuallyTakes() {
        // Successful Deploy runs took 10 and 4 minutes: the median of two is the longer one.
        assertEquals(10.minutes, GitHubRuns.typicalDuration(runs.first(), runs))
        val running = GitHubRuns.toDeployRun(runs.first(), runs, emptyMap(), now).outcome as DeployOutcome.InProgress
        assertEquals(2.minutes, running.elapsed)
        assertEquals(0.2f, running.progress!!, 0.001f)

        val overdue = GitHubRuns.toDeployRun(runs.first(), runs, emptyMap(), now.plusSeconds(3600)).outcome as DeployOutcome.InProgress
        assertEquals("never claims to be done", 0.95f, overdue.progress!!, 0.001f)

        val noHistory = GitHubRuns.toDeployRun(runs.first(), emptyList(), emptyMap(), now).outcome as DeployOutcome.InProgress
        assertNull(noHistory.progress)
    }

    @Test
    fun mapsPassedAndFailedRuns() {
        val passed = GitHubRuns.toDeployRun(runs[3], runs, emptyMap(), now)
        assertEquals("rxflow", passed.repo)
        assertEquals("Deploy", passed.workflow)
        assertEquals(DeployOutcome.Passed(took = 10.minutes, ago = 160.minutes), passed.outcome)

        val failed = runs.single { it.id == 2L }
        assertEquals("integration", (GitHubRuns.toDeployRun(failed, runs, mapOf(2L to "integration"), now).outcome as DeployOutcome.Failed).failedStep)
        assertNull((GitHubRuns.toDeployRun(failed, runs, emptyMap(), now).outcome as DeployOutcome.Failed).failedStep)
    }

    @Test
    fun findsTheFailedJob() {
        val jobs = """{"jobs":[{"name":"lint","conclusion":"success"},{"name":"e2e-tests","conclusion":"failure"},{"name":"x","conclusion":"failure"}]}"""
        assertEquals("e2e-tests", GitHubRuns.failedJob(jobs))
        assertNull(GitHubRuns.failedJob("""{"jobs":[]}"""))
    }

    @Test
    fun sourceAsksTheNetworkOncePerIntervalAndSendsTheTokenOnlyToGitHub() = runTest {
        var clock = now
        val asked = mutableListOf<String>()
        val source = GitHubDeploySource("secret", listOf("example-org/RxFlow"), { clock }, Duration.ofSeconds(45)) { url, headers ->
            assertTrue(url.startsWith("https://api.github.com/"))
            assertEquals("Bearer secret", headers["Authorization"])
            asked += url
            if ("/jobs" in url) """{"jobs":[]}""" else reply
        }
        assertEquals(4, source.recentRuns().size)
        clock = clock.plusSeconds(10)
        val ticking = source.recentRuns().first().outcome as DeployOutcome.InProgress
        assertEquals("the timer moves between fetches", 130.seconds, ticking.elapsed)
        assertEquals(1, asked.size)
        clock = clock.plusSeconds(40)
        source.recentRuns()
        assertEquals(2, asked.size)
    }

    @Test
    fun sourceReportsARejectedTokenAndDoesNotHammerGitHub() = runTest {
        var clock = now
        var calls = 0
        val source = GitHubDeploySource("old", listOf("a/b"), { clock }, Duration.ofSeconds(45)) { url, _ ->
            calls++
            throw HttpStatusException(401, url)
        }
        repeat(3) {
            try {
                source.recentRuns()
                fail("expected a token failure")
            } catch (e: DeploySourceException) {
                assertEquals(DeployFailure.TokenRejected, e.failure)
            }
            clock = clock.plusSeconds(1)
        }
        assertEquals(1, calls)
    }
}
