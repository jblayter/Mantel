package com.blayter.mantel.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MantelConfigTest {
    private fun ok(text: String) = (parseConfig(text) as ConfigParseResult.Ok).config
    private fun invalid(text: String) = (parseConfig(text) as ConfigParseResult.Invalid).reason

    @Test
    fun readsTheGlancesAddress() {
        val config = ok("""{ "glancesUrl": "http://192.168.6.35:61208" }""")
        assertEquals("http://192.168.6.35:61208", config.glancesUrl)
        assertEquals("192.168.6.35", config.glancesHost)
    }

    @Test
    fun acceptsTheApiPathATrailingSlashAndUnknownKeys() {
        assertEquals("http://box:61208", ok("""{"glancesUrl":"http://box:61208/api/4/","later":1}""").glancesUrl)
        assertEquals("http://box:61208", ok("""{"glancesUrl":" http://box:61208/ "}""").glancesUrl)
    }

    @Test
    fun saysWhatIsWrong() {
        assertTrue("JSON" in invalid("glancesUrl = http://box"))
        assertTrue("glancesUrl" in invalid("{}"))
        assertTrue("glancesUrl" in invalid("""{"glancesUrl": 5}"""))
        assertTrue("http://" in invalid("""{"glancesUrl":"192.168.6.35:61208"}"""))
        assertTrue("http://" in invalid("""{"glancesUrl":"ftp://box"}"""))
        assertTrue("host" in invalid("""{"glancesUrl":"http://:61208"}"""))
    }

    @Test
    fun readsTheGitHubTokenAndRepositories() {
        val config = ok("""{"glancesUrl":"http://box:61208","githubToken":" abc123 ","repos":["example-org/RxFlow","me/reach"]}""")
        assertEquals("abc123", config.githubToken)
        assertEquals(listOf("example-org/RxFlow", "me/reach"), config.repos)
        assertTrue("the token never appears in logs", "abc123" !in config.toString())

        val without = ok("""{"glancesUrl":"http://box:61208"}""")
        assertEquals(null, without.githubToken)
        assertTrue(without.repos.isEmpty())
    }

    @Test
    fun tokenAndRepositoriesGoTogether() {
        assertTrue("no \"repos\"" in invalid("""{"glancesUrl":"http://box","githubToken":"abc"}"""))
        assertTrue("no \"githubToken\"" in invalid("""{"glancesUrl":"http://box","repos":["a/b"]}"""))
        assertTrue("owner/name" in invalid("""{"glancesUrl":"http://box","githubToken":"abc","repos":["RxFlow"]}"""))
        assertTrue("list" in invalid("""{"glancesUrl":"http://box","githubToken":"abc","repos":"a/b"}"""))
    }

    @Test
    fun readsSeveralMachines() {
        val config = ok("""{"glancesUrls":["http://192.168.1.50:61208/","http://192.168.1.61:61208","http://192.168.1.50:61208"]}""")
        assertEquals(listOf("http://192.168.1.50:61208", "http://192.168.1.61:61208"), config.glancesUrls)
        assertEquals("http://192.168.1.50:61208", config.glancesUrl)
        assertEquals(listOf("http://box:61208"), ok("""{"glancesUrl":"http://box:61208"}""").glancesUrls)

        assertTrue("both" in invalid("""{"glancesUrl":"http://a","glancesUrls":["http://b"]}"""))
        assertTrue("glancesUrls" in invalid("""{"glancesUrls":[]}"""))
        assertTrue("glancesUrls" in invalid("""{"glancesUrls":["http://a", 5]}"""))
        assertTrue("http://" in invalid("""{"glancesUrls":["http://a","b:61208"]}"""))
    }
}
