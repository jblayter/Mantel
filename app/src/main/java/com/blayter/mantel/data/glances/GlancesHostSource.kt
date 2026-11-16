package com.blayter.mantel.data.glances

import com.blayter.mantel.data.HostSnapshot
import com.blayter.mantel.data.HostSource
import com.blayter.mantel.data.httpGet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The CI box, read through the Glances 4 REST API. Only ever sends GET
 * requests: Mantel is read-only.
 *
 * @param glancesUrl root of the Glances web server, e.g. `http://192.168.6.35:61208`.
 * @param get fetches one URL as text, throwing on any failure.
 */
class GlancesHostSource(
    glancesUrl: String,
    private val get: suspend (String) -> String = { httpGet(it) },
) : HostSource {
    private val api = "${glancesUrl.trimEnd('/')}/api/4"

    /** The box's name does not change, so `/system` is asked once. */
    private var hostName: String? = null

    override suspend fun snapshot(): HostSnapshot = coroutineScope {
        val containers = async { get("$api/containers") }
        val quicklook = async { get("$api/quicklook") }
        val mem = async { get("$api/mem") }
        val load = async { get("$api/load") }
        val name = hostName ?: GlancesParser.hostName(get("$api/system")).also { hostName = it }
        GlancesParser.snapshot(name, containers.await(), quicklook.await(), mem.await(), load.await())
    }
}
