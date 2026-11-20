package com.blayter.mantel.data.github

import com.blayter.mantel.data.DeployFailure
import com.blayter.mantel.data.DeploySource
import com.blayter.mantel.data.DeploySourceException
import com.blayter.mantel.data.HttpStatusException
import com.blayter.mantel.data.httpGet
import com.blayter.mantel.domain.DeployRun
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant

/**
 * Workflow runs from the GitHub REST API. Read-only: it only lists runs and
 * the jobs of a failed run, and the token needs nothing beyond read access to
 * Actions and Metadata.
 *
 * [recentRuns] is called every second so the running timer ticks; the network
 * is only asked once per [pollEvery], for both successes and failures.
 *
 * @param token a token that works now; asked before each fetch, so a signed-in display can refresh it.
 * @param repos `owner/name` each; asked before each fetch, so the list can change on GitHub.
 * @param get fetches one URL as text with the given headers.
 */
class GitHubDeploySource(
    private val token: suspend () -> String,
    private val repos: () -> List<String>,
    private val now: () -> Instant = Instant::now,
    private val pollEvery: Duration = Duration.ofSeconds(45),
    private val get: suspend (url: String, headers: Map<String, String>) -> String = ::httpGet,
) : DeploySource {
    /** A token and a list that never change: the pushed config file. */
    constructor(
        token: String,
        repos: List<String>,
        now: () -> Instant = Instant::now,
        pollEvery: Duration = Duration.ofSeconds(45),
        get: suspend (url: String, headers: Map<String, String>) -> String = ::httpGet,
    ) : this({ token }, { repos }, now, pollEvery, get)

    private var history: List<WorkflowRun>? = null
    private var lastAttemptAt: Instant? = null
    private var lastFailure: Exception? = null

    /** Run id to failed job name. A finished run never changes, so each is asked for once. */
    private val failedJobs = HashMap<Long, String>()
    private val failedJobsAsked = HashSet<Long>()

    override suspend fun recentRuns(): List<DeployRun> {
        val t = now()
        val last = lastAttemptAt
        if (last == null || Duration.between(last, t) >= pollEvery) {
            lastAttemptAt = t
            lastFailure = try {
                fetch(t)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                if (e.code == 401) DeploySourceException(DeployFailure.TokenRejected) else e
            } catch (e: Exception) {
                e
            }
        }
        lastFailure?.let { throw it }
        val all = history.orEmpty()
        return GitHubRuns.shown(all, t).map { GitHubRuns.toDeployRun(it, all, failedJobs, t) }
    }

    private suspend fun fetch(t: Instant) {
        val headers = mapOf(
            "Authorization" to "Bearer ${token()}",
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
        )
        val all = repos().flatMap { repo ->
            GitHubRuns.parseRuns(repo, get("$API/repos/$repo/actions/runs?per_page=$PAGE_SIZE", headers))
        }
        for (run in GitHubRuns.shown(all, t)) {
            if (run.isFailure && failedJobsAsked.add(run.id)) {
                // Which job failed is a nicety; the card says plain "Failed" without it.
                try {
                    val jobs = get("$API/repos/${run.repo}/actions/runs/${run.id}/jobs?filter=latest&per_page=100", headers)
                    GitHubRuns.failedJob(jobs)?.let { failedJobs[run.id] = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failedJobsAsked.remove(run.id)
                }
            }
        }
        history = all
    }

    private companion object {
        const val API = "https://api.github.com"

        /** Enough recent runs per repository to fill the card and to learn how long each workflow takes. */
        const val PAGE_SIZE = 50
    }
}
