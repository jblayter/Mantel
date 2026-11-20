package com.blayter.mantel.data.glances

import com.blayter.mantel.data.HttpStatusException
import com.blayter.mantel.domain.FoundMachine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Runs the scan against a made-up network: one real Glances (captured replies), one locked, one web server. */
class GlancesScannerTest {
    private fun reply(name: String) =
        checkNotNull(javaClass.getResource("/glances/$name.json")) { "missing fixture $name" }.readText()

    private val listening = setOf("192.168.1.38", "192.168.1.40", "192.168.1.50", "192.168.1.60")
    private val asked = mutableListOf<String>()

    private val scanner = GlancesScanner(
        portOpen = { address, _ -> address in listening },
        get = { url ->
            asked += url
            when {
                "192.168.1.38" in url -> reply(url.substringAfterLast('/'))
                "192.168.1.40" in url -> throw HttpStatusException(401, url)
                "192.168.1.50" in url -> "<html>a printer</html>"
                else -> throw IOException("reset")
            }
        },
    )

    @Test
    fun aGlancesMachineIsFoundWithItsFacts() = runTest {
        val found = (scanner.probe("192.168.1.38") as ProbeResult.Found).machine
        val expected = GlancesParser.snapshot(
            GlancesParser.hostName(reply("system")), reply("containers"), reply("quicklook"), reply("mem"), reply("load"),
        )
        assertEquals("192.168.1.38", found.address)
        assertEquals("http://192.168.1.38:61208", found.url)
        assertEquals(expected.host.name, found.name)
        assertEquals(expected.host.threads, found.threads)
        assertEquals(expected.host.memoryTotalGb, found.memoryGb)
        assertEquals(expected.containers.size, found.containers)
    }

    @Test
    fun everythingElseIsNoAnswerOrAPassword() = runTest {
        assertEquals(ProbeResult.NoAnswer, scanner.probe("192.168.1.2"))
        assertEquals(ProbeResult.NeedsPassword, scanner.probe("192.168.1.40"))
        assertEquals(ProbeResult.NoAnswer, scanner.probe("192.168.1.50"))
        assertEquals(ProbeResult.NoAnswer, scanner.probe("192.168.1.60"))
    }

    @Test
    fun theScanAsksEveryAddressAndOnlyEverReads() = runTest {
        val found = mutableListOf<FoundMachine>()
        val progress = mutableListOf<Int>()
        val addresses = (1..254).map { "192.168.1.$it" }
        scanner.scan(addresses, onChecked = { progress += it }, onFound = { found += it })
        assertEquals((1..254).toList(), progress)
        assertEquals(listOf("192.168.1.38"), found.map { it.address })
        // Only addresses with the port open are sent a request, and only under /api/4.
        assertTrue(asked.isNotEmpty())
        assertTrue(asked.all { url -> listening.any { "http://$it:61208/api/4/" in url } })
    }

    @Test
    fun anotherPortIsAskedWhenGiven() = runTest {
        val onOtherPort = GlancesScanner(portOpen = { _, port -> port == 61209 }, get = { reply(it.substringAfterLast('/')) })
        assertEquals(ProbeResult.NoAnswer, onOtherPort.probe("10.0.2.2"))
        val found = (onOtherPort.probe("10.0.2.2", 61209) as ProbeResult.Found).machine
        assertEquals("10.0.2.2:61209", found.shownAddress)
    }
}
