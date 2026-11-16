package com.blayter.mantel.data.github

import com.blayter.mantel.domain.DeployOutcome
import com.blayter.mantel.domain.DeployRun
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Duration as JavaDuration
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.days

/** One workflow run as GitHub reports it, with absolute times. */
data class WorkflowRun(
    val id: Long,
    val repo: String,
    val workflowId: Long,
    val workflow: String,
    val commitTitle: String,
    val branch: String,
    /** `completed`, or one of the not-finished states (`queued`, `in_progress`, `waiting`, ...). */
    val status: String,
    /** Set once completed: `success`, `failure`, `cancelled`, `skipped`, ... */
    val conclusion: String?,
    val startedAt: Instant,
    val updatedAt: Instant,
) {
    val isRunning get() = status != "completed"
    val isSuccess get() = !isRunning && conclusion == "success"
    val isFailure get() = !isRunning && conclusion in FailedConclusions

    private companion object {
        val FailedConclusions = setOf("failure", "timed_out", "startup_failure")
    }
}

/**
 * Pure rules for the deploys card, tested without a network.
 *
 * Shown: runs still going, runs that passed and runs that failed, from every
 * workflow. Not shown: skipped, cancelled and other neutral endings, which
 * are neither "it went out" nor "it broke".
 */
object GitHubRuns {
    /** How far back the card looks before it says "No recent runs". */
    val RECENT_WINDOW: Duration = 7.days

    /** Rows the deploys card has room for. */
    const val MAX_SHOWN = 3

    /** A running bar never claims to be finished. */
    private const val PROGRESS_CAP = 0.95f

    /** `GET /repos/{owner}/{repo}/actions/runs`. [repo] is `owner/name`. */
    fun parseRuns(repo: String, text: String): List<WorkflowRun> {
        val runs = Json.parseToJsonElement(text).jsonObject["workflow_runs"] as? JsonArray ?: return emptyList()
        return runs.mapNotNull { element ->
            val r = element as? JsonObject ?: return@mapNotNull null
            val started = r.instant("run_started_at") ?: r.instant("created_at") ?: return@mapNotNull null
            WorkflowRun(
                id = r.long("id") ?: return@mapNotNull null,
                repo = repo,
                workflowId = r.long("workflow_id") ?: 0,
                workflow = r.string("name") ?: "workflow",
                // display_title is just the workflow's name for runs started by another workflow.
                commitTitle = (r["head_commit"] as? JsonObject)?.string("message")?.lineSequence()?.firstOrNull()?.trim()
                    ?: r.string("display_title").orEmpty(),
                branch = r.string("head_branch").orEmpty(),
                status = r.string("status") ?: "completed",
                conclusion = r.string("conclusion"),
                startedAt = started,
                updatedAt = r.instant("updated_at") ?: started,
            )
        }
    }

    /** `GET .../actions/runs/{id}/jobs`: the name of the first job that failed, if GitHub says. */
    fun failedJob(text: String): String? {
        val jobs = Json.parseToJsonElement(text).jsonObject["jobs"] as? JsonArray ?: return null
        return jobs.firstNotNullOfOrNull { job ->
            (job as? JsonObject)?.takeIf { it.string("conclusion") == "failure" }?.string("name")
        }
    }

    /** The runs to put on the card: still going first, then newest; inside the window; at most [MAX_SHOWN]. */
    fun shown(runs: List<WorkflowRun>, now: Instant): List<WorkflowRun> = runs
        .filter { it.isRunning || it.isSuccess || it.isFailure }
        .filter { it.isRunning || between(it.updatedAt, now) <= RECENT_WINDOW }
        .sortedWith(compareByDescending<WorkflowRun> { it.isRunning }.thenByDescending { it.startedAt })
        .take(MAX_SHOWN)

    /**
     * How long this run's workflow usually takes: the median of its
     * successful runs in [history]. Null when it has never been seen to pass.
     */
    fun typicalDuration(run: WorkflowRun, history: List<WorkflowRun>): Duration? {
        val took = history
            .filter { it.repo == run.repo && it.workflowId == run.workflowId && it.isSuccess }
            .map { between(it.startedAt, it.updatedAt) }
            .filter { it > Duration.ZERO }
            .sorted()
        return took.getOrNull(took.size / 2)
    }

    /**
     * @param failedJobs run id to the job that failed, for the failed runs it is known for.
     */
    fun toDeployRun(run: WorkflowRun, history: List<WorkflowRun>, failedJobs: Map<Long, String>, now: Instant): DeployRun {
        val outcome = when {
            run.isRunning -> {
                val elapsed = between(run.startedAt, now)
                // GitHub reports no progress. This is an estimate: time so far against the usual time.
                val progress = typicalDuration(run, history)?.let { (elapsed / it).toFloat().coerceIn(0f, PROGRESS_CAP) }
                DeployOutcome.InProgress(elapsed, progress)
            }
            run.isFailure -> DeployOutcome.Failed(failedJobs[run.id], between(run.updatedAt, now))
            else -> DeployOutcome.Passed(between(run.startedAt, run.updatedAt), between(run.updatedAt, now))
        }
        return DeployRun(
            repo = shortRepo(run.repo),
            workflow = run.workflow,
            commitTitle = run.commitTitle,
            branch = run.branch,
            outcome = outcome,
        )
    }

    /** `Factory55/RxFlow` is "rxflow" on the card, as in the mockup. */
    fun shortRepo(repo: String): String = repo.substringAfter('/').lowercase()

    private fun between(from: Instant, to: Instant): Duration =
        JavaDuration.between(from, to).toMillis().coerceAtLeast(0).milliseconds

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.instant(key: String): Instant? =
        string(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
