package com.blayter.mantel.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** One repository's page on the deploys card. */
data class RepoDeploys(val repo: String, val runs: List<DeployRun>)

/**
 * What the deploys card pages through.
 *
 * [active] is true while anything is deploying or has finished within
 * [DeployRules.ACTIVE_WINDOW]: the pages are then only the repositories with
 * such runs, and only those runs. Otherwise (the maintenance window) every
 * repository has a page with its latest [DeployRules.MAINTENANCE_ROWS] runs.
 */
data class DeployBoard(val active: Boolean, val pages: List<RepoDeploys>)

object DeployRules {
    /** A run counts as active while it is going and for this long after it ends. */
    val ACTIVE_WINDOW: Duration = 1.hours

    /** Runs per repository in the maintenance window. */
    const val MAINTENANCE_ROWS = 4
}

/** Still going, or passed or failed within the active window. */
val DeployRun.isActive: Boolean
    get() = when (val o = outcome) {
        is DeployOutcome.InProgress -> true
        is DeployOutcome.Failed -> o.ago <= DeployRules.ACTIVE_WINDOW
        is DeployOutcome.Passed -> o.ago <= DeployRules.ACTIVE_WINDOW
    }

/**
 * Groups [runs] into pages. Repositories keep the order they first appear in,
 * and runs keep the order the source gave them (still going first, then newest).
 */
fun deployBoard(runs: List<DeployRun>): DeployBoard {
    val byRepo = runs.groupBy { it.repo }
    val active = byRepo.mapNotNull { (repo, all) ->
        all.filter { it.isActive }.takeIf { it.isNotEmpty() }?.let { RepoDeploys(repo, it) }
    }
    if (active.isNotEmpty()) return DeployBoard(active = true, pages = active)
    return DeployBoard(
        active = false,
        pages = byRepo.map { (repo, all) -> RepoDeploys(repo, all.take(DeployRules.MAINTENANCE_ROWS)) },
    )
}

/**
 * The page to show: [wanted] while it still has a page, otherwise the first.
 * Null when there are no pages.
 */
fun pageToShow(repos: List<String>, wanted: String?): String? = wanted?.takeIf { it in repos } ?: repos.firstOrNull()

/** The page [step] places after [current], wrapping round. With one page it is that page. */
fun pageAfter(repos: List<String>, current: String?, step: Int = 1): String? {
    if (repos.isEmpty()) return null
    val at = repos.indexOf(current).coerceAtLeast(0)
    return repos[(at + step).mod(repos.size)]
}
