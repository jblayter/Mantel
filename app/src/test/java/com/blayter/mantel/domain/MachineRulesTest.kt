package com.blayter.mantel.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class MachineRulesTest {
    private val well = MachineSummary("a", MachineCondition.Well(5))
    private val coral = MachineSummary("b", MachineCondition.Warning("db is at 92% of its 2 GB memory cap", Severity.Wrong, 1))
    private val amber = MachineSummary("c", MachineCondition.Warning("x has held about 9 cores for 12 min", Severity.Busy, 2))
    private val silent = MachineSummary("d", MachineCondition.NotAnswering("has not answered for 4 min"))

    @Test
    fun turnsPassOverMachinesThatAreNotAnswering() {
        val machines = listOf(well, silent, coral)
        assertEquals(2, nextTurn(0, machines))
        assertEquals(0, nextTurn(2, machines))
        assertEquals("with nobody else answering, the current machine stays", 0, nextTurn(0, listOf(well, silent)))
        assertEquals(0, nextTurn(0, listOf(well)))
    }

    @Test
    fun aCoralWarningGetsTheLongerTurn() {
        assertEquals(30.seconds, turnLength(well, 30.seconds, 60.seconds))
        assertEquals(60.seconds, turnLength(coral, 30.seconds, 60.seconds))
        assertEquals("amber is busy, not wrong", 30.seconds, turnLength(amber, 30.seconds, 60.seconds))
    }

    @Test
    fun theRemoteReachesAnyMachineWithAReading() {
        val has = listOf(true, false, true, true)
        assertEquals(2, stepMachine(0, has, 1))
        assertEquals(3, stepMachine(0, has, -1))
        assertEquals(0, stepMachine(3, has, 1))
        assertEquals(0, stepMachine(0, listOf(true, false), 1))
    }

    @Test
    fun plaqueSentences() {
        assertEquals("All well · 5 containers", plaqueSentence(well, onScreen = false, paused = false))
        assertEquals("All well · no containers", plaqueSentence(MachineSummary("p", MachineCondition.Well(0)), false, false))
        assertEquals("db is at 92% of its 2 GB memory cap", plaqueSentence(coral, false, false))
        assertEquals("has not answered for 4 min", plaqueSentence(silent, false, false))
        assertEquals("On screen · 2 warnings", plaqueSentence(amber, true, false))
        assertEquals("On screen · all well", plaqueSentence(well, true, false))
        assertEquals("On screen · paused", plaqueSentence(coral, true, true))
    }

    @Test
    fun upToFourMachinesEachGetAPlaqueInOrder() {
        assertEquals(List(4) { Plaque.Machine(it) }, shelfPlaques(listOf(well, coral, silent, well), onScreen = 2))
    }

    @Test
    fun withMoreTheShelfShowsOnScreenThenTroubleThenTheRest() {
        val six = listOf(well, coral, silent, well, well, well)
        assertEquals(
            listOf(Plaque.Machine(0), Plaque.Machine(1), Plaque.Machine(2), Plaque.More(3, needAttention = 0)),
            shelfPlaques(six, onScreen = 0),
        )
        val manyInTrouble = listOf(well, coral, silent, amber, well)
        assertEquals(
            listOf(Plaque.Machine(4), Plaque.Machine(1), Plaque.Machine(2), Plaque.More(2, needAttention = 1)),
            shelfPlaques(manyInTrouble, onScreen = 4),
        )
    }

    @Test
    fun summaryOfAMachineThatHasNeverAnswered() {
        val summary = machineSummary(null, "192.168.1.77", memoryNearCapPercent = 85)
        assertEquals("192.168.1.77", summary.name)
        assertEquals(MachineCondition.NotAnswering("has not answered yet"), summary.condition)
    }
}
