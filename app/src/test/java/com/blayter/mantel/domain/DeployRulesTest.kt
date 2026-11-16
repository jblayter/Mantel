package com.blayter.mantel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class DeployRulesTest {
    private fun passed(repo: String, ago: Duration, workflow: String = "CI") =
        DeployRun(repo, workflow, "Fix", "main", DeployOutcome.Passed(2.minutes, ago))

    private fun failed(repo: String, ago: Duration) =
        DeployRun(repo, "Deploy", "Fix", "main", DeployOutcome.Failed("e2e", ago))

    private fun running(repo: String, elapsed: Duration = 3.minutes) =
        DeployRun(repo, "Deploy", "Fix", "main", DeployOutcome.InProgress(elapsed, null))

    @Test
    fun aRunIsActiveWhileGoingAndForAnHourAfter() {
        assertTrue(running("a").isActive)
        assertTrue("however long it has been going", running("a", elapsed = 3.hours).isActive)
        assertTrue(passed("a", 60.minutes).isActive)
        assertTrue(failed("a", 59.minutes).isActive)
        assertFalse(passed("a", 61.minutes).isActive)
        assertFalse(failed("a", 2.hours).isActive)
    }

    @Test
    fun activeBoardHasOnlyActiveRepositoriesAndTheirActiveRuns() {
        val board = deployBoard(
            listOf(
                running("rxflow"), passed("rxflow", 20.minutes), passed("rxflow", 5.hours),
                passed("reach", 2.hours),
                failed("krakentv", 30.minutes),
            ),
        )
        assertTrue(board.active)
        assertEquals(listOf("rxflow", "krakentv"), board.pages.map { it.repo })
        assertEquals(2, board.pages[0].runs.size)
        assertEquals(1, board.pages[1].runs.size)
    }

    @Test
    fun maintenanceBoardHasEveryRepositoryWithItsLatestFour() {
        val board = deployBoard(List(6) { passed("rxflow", (2 + it).hours, "run $it") } + failed("reach", 3.hours))
        assertFalse(board.active)
        assertEquals(listOf("rxflow", "reach"), board.pages.map { it.repo })
        assertEquals(listOf("run 0", "run 1", "run 2", "run 3"), board.pages[0].runs.map { it.workflow })
        assertEquals(1, board.pages[1].runs.size)
    }

    @Test
    fun nothingToShowIsAnEmptyQuietBoard() {
        assertEquals(DeployBoard(active = false, pages = emptyList()), deployBoard(emptyList()))
    }

    @Test
    fun pagesWrapAndASinglePageStays() {
        val repos = listOf("a", "b", "c")
        assertEquals("b", pageAfter(repos, "a"))
        assertEquals("a", pageAfter(repos, "c"))
        assertEquals("c", pageAfter(repos, "a", step = -1))
        assertEquals("a", pageAfter(listOf("a"), "a"))
        assertNull(pageAfter(emptyList(), "a"))
    }

    @Test
    fun aPageThatHasGoneGivesWayToTheFirst() {
        assertEquals("b", pageToShow(listOf("a", "b"), "b"))
        assertEquals("a", pageToShow(listOf("a", "b"), "gone"))
        assertEquals("a", pageToShow(listOf("a", "b"), null))
        assertNull(pageToShow(emptyList(), "a"))
    }
}
