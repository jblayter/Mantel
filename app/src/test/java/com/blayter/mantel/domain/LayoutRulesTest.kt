package com.blayter.mantel.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class LayoutRulesTest {
    private fun live(name: String, cores: Double = 0.5, memoryMb: Int = 100, capMb: Int? = null, sustained: Boolean = false) =
        ContainerStatus(
            name, name, ContainerKind.Runner, Presence.Live(10.minutes), cores = cores, memoryMb = memoryMb,
            memoryCapMb = capMb, sustainedFor = if (sustained) 12.minutes else null,
        )

    @Test
    fun flagsNameTheWorstState() {
        assertEquals(ContainerFlag.Missing, flagOf(ContainerStatus("a", "a", ContainerKind.Runner, Presence.Missing(3.minutes)), 85))
        assertEquals(ContainerFlag.Ended, flagOf(ContainerStatus("b", "b", ContainerKind.Job, Presence.Ended(20.minutes)), 85))
        // Near its cap and busy: the cap is the worse news.
        assertEquals(ContainerFlag.NearCap, flagOf(live("c", memoryMb = 3700, capMb = 4096, sustained = true), 85))
        assertEquals(ContainerFlag.Busy, flagOf(live("d", cores = 9.0, sustained = true), 85))
        assertEquals(ContainerFlag.Ok, flagOf(live("e"), 85))
    }

    @Test
    fun countsLeaveEndedOut() {
        val containers = listOf(
            ContainerStatus("a", "a", ContainerKind.Runner, Presence.Missing(3.minutes)),
            live("b", memoryMb = 3900, capMb = 4096),
            live("c", sustained = true),
            live("d"),
            live("e"),
            ContainerStatus("f", "f", ContainerKind.Job, Presence.Ended(1.minutes)),
        )
        assertEquals(SeverityCounts(wrong = 2, busy = 1, ok = 2), severityCounts(containers, 85))
    }

    private fun run(repo: String, workflow: String, going: Boolean = false) = DeployRun(
        repo, workflow, "title", "main",
        if (going) DeployOutcome.InProgress(2.minutes, 0.5f) else DeployOutcome.Passed(3.minutes, 10.minutes),
    )

    @Test
    fun activeRowsPutRunningFirst() {
        val board = DeployBoard(
            active = true,
            pages = listOf(
                RepoDeploys("a", listOf(run("a", "ci"))),
                RepoDeploys("b", listOf(run("b", "deploy", going = true))),
            ),
        )
        assertEquals(listOf("deploy", "ci"), deployRowsAtOnce(board).map { it.workflow })
    }

    @Test
    fun maintenanceRowsTakeEachRepositoryInTurn() {
        val board = DeployBoard(
            active = false,
            pages = listOf(
                RepoDeploys("a", listOf(run("a", "a1"), run("a", "a2"), run("a", "a3"))),
                RepoDeploys("b", listOf(run("b", "b1"))),
                RepoDeploys("c", listOf(run("c", "c1"), run("c", "c2"))),
            ),
        )
        assertEquals(listOf("a1", "b1", "c1", "a2", "c2", "a3"), deployRowsAtOnce(board).map { it.workflow })
    }

    @Test
    fun segmentsLightAtLeastOneForAnythingAboveZero() {
        assertEquals(0, litSegments(0f, 10))
        assertEquals(1, litSegments(0.01f, 10))
        assertEquals(5, litSegments(0.5f, 10))
        assertEquals(10, litSegments(1.4f, 10))
    }
}
