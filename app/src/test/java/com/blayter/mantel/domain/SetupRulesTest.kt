package com.blayter.mantel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRulesTest {
    private fun machine(address: String, port: Int = GLANCES_PORT) = FoundMachine(address, port, "box", 8, 15, 5)

    @Test
    fun scansTheTvsOwnNetwork() {
        assertEquals("192.168.1.", networkPrefix("192.168.1.23"))
        assertNull(networkPrefix("not an address"))
        assertNull(networkPrefix("192.168.1"))
        assertNull(networkPrefix("192.168.1.256"))
        val addresses = scanAddresses("192.168.1.23")
        assertEquals(254, addresses.size)
        assertEquals("192.168.1.1", addresses.first())
        assertEquals("192.168.1.254", addresses.last())
        assertEquals(emptyList<String>(), scanAddresses("fe80::1"))
    }

    @Test
    fun machinesAreListedByAddressNumberByNumber() {
        val sorted = sortedForPicker(listOf(machine("192.168.1.61"), machine("192.168.1.7", 61209), machine("192.168.1.7"), machine("192.168.1.100")))
        assertEquals(listOf("192.168.1.7", "192.168.1.7:61209", "192.168.1.61", "192.168.1.100"), sorted.map { it.shownAddress })
        assertEquals("http://192.168.1.7:61209", sorted[1].url)
    }

    @Test
    fun sentencesAndButtonWords() {
        assertEquals("Looking for machines on your network.", pickerSentence(0, searching = true))
        assertEquals("Three answered on your network. Press OK on a cell to watch it or stop.", pickerSentence(3, searching = true))
        assertEquals("One answered on your network. Press OK on a cell to watch it or stop.", pickerSentence(1, searching = false))
        assertTrue(pickerSentence(9, searching = false).startsWith("Nine answered on your network; the first seven are shown."))
        assertEquals("14", countWord(14))
        assertNull(watchLabel(0))
        assertEquals("Watch 1 machine", watchLabel(1))
        assertEquals("Watch 2 machines", watchLabel(2))
    }

    @Test
    fun onlyTheLastNumberIsTypedAtFirst() {
        var entry = AddressEntry(prefix = "192.168.1.")
        assertFalse(entry.isComplete)
        entry = entry.press('5').press('.').press('7')
        assertEquals("192.168.1.57", entry.address)
        assertTrue(entry.isComplete)
        // 579 is not a number an address can hold, and neither is 05.
        assertEquals("57", entry.press('9').typed)
        assertEquals("0", AddressEntry("10.0.2.").press('0').press('5').typed)
        assertEquals("192.168.1.5", entry.delete().address)
        assertEquals("", entry.delete().delete().delete().typed)
    }

    @Test
    fun theFirstPartCanBeChanged() {
        var entry = AddressEntry(prefix = "192.168.1.").press('5').changeFirstPart()
        assertEquals("", entry.prefix)
        assertEquals("192.168.1.5", entry.typed)
        repeat(9) { entry = entry.delete() }
        assertEquals("19", entry.typed)
        entry = "..2.16.9..31.4".fold(entry) { e, key -> e.press(key) }
        // A second dot in a row, a fifth number and a number over 255 are all refused.
        assertEquals("19.2.16.93", entry.typed)
        assertTrue(entry.isComplete)
    }

    @Test
    fun aTvWithNoAddressTypesTheWholeThing() {
        val entry = "10.0.0.8".fold(AddressEntry(prefix = "")) { e, key -> e.press(key) }
        assertEquals(AddressField.Whole, entry.field)
        assertEquals("10.0.0.8", entry.address)
        assertTrue(entry.isComplete)
        assertEquals("", AddressEntry(prefix = "").press('.').typed)
    }

    @Test
    fun thePortIsChangedOnRequest() {
        var entry = AddressEntry(prefix = "192.168.1.").press('5').togglePort()
        assertEquals(AddressField.Port, entry.field)
        entry = entry.press('1')
        assertEquals("61208", entry.port)
        entry = entry.delete().press('9')
        assertEquals(61209, entry.portNumber)
        assertEquals("5", entry.typed)
        entry = entry.togglePort()
        assertEquals(AddressField.LastNumber, entry.field)
        assertEquals("192.168.1.57", entry.press('7').address)
        // No port, no check.
        val noPort = "12345".fold(AddressEntry("10.0.2.").press('2').togglePort()) { e, _ -> e.delete() }
        assertNull(noPort.portNumber)
        assertFalse(noPort.isComplete)
        assertEquals(AddressField.Whole, AddressEntry("10.0.2.").changeFirstPart().togglePort().togglePort().field)
    }

    @Test
    fun saysHowLongTheCodeStillWorks() {
        assertEquals("This code works for 14 more minutes.", codeTimeLeft(899))
        assertEquals("This code works for 1 more minute.", codeTimeLeft(119))
        assertEquals("This code works for less than a minute more.", codeTimeLeft(59))
    }

    @Test
    fun readyScreenWords() {
        assertEquals("Mantel shows ci-runner01. Deploys stay on screen the whole time.", readySentence(listOf("ci-runner01"), true))
        assertEquals("Mantel takes turns between the two machines.", readySentence(listOf("a", "b"), false))
        assertEquals("Watching 1 machine", watchingHeading(1))
        assertEquals("Watching 3 machines", watchingHeading(3))
        assertEquals("Deploys from 1 repository · signed in as octocat", deploysHeading(1, "octocat"))
        assertEquals("Deploys from 3 repositories · signed in as octocat", deploysHeading(3, "octocat"))
        assertEquals("Signed in as octocat · no repositories yet", deploysHeading(0, "octocat"))
    }

    @Test
    fun aLongListEndsWithHowManyAreLeftOut() {
        val ten = (1..10).map { "r$it" }
        assertEquals(ten to 0, readyLines(ten))
        assertEquals(ten.take(9) to 3, readyLines(ten + listOf("r11", "r12")))
    }

    @Test
    fun pickingFromSettingsKeepsTheFirstMachineFirst() {
        val stored = listOf("http://10.0.0.38:61208", "http://10.0.0.9:61208")
        val picker = listOf("http://10.0.0.5:61208", "http://10.0.0.9:61208", "http://10.0.0.38:61208")
        assertEquals(
            "a new machine goes after the ones already watched",
            stored + "http://10.0.0.5:61208",
            machinesAfterPicking(stored, picker.toSet(), picker),
        )
        assertEquals(
            "an unpicked machine goes",
            listOf("http://10.0.0.38:61208"),
            machinesAfterPicking(stored, setOf("http://10.0.0.38:61208"), picker),
        )
        assertEquals(
            "one the search did not find stays",
            stored,
            machinesAfterPicking(stored, stored.toSet(), listOf("http://10.0.0.38:61208")),
        )
    }

    @Test
    fun saysHowManyRepositoriesAreRead() {
        assertEquals("Mantel reads no repositories yet.", repositoriesNow(0))
        assertEquals("Mantel reads 1 repository now.", repositoriesNow(1))
        assertEquals("Mantel reads 3 repositories now.", repositoriesNow(3))
    }
}
