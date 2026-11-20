package com.blayter.mantel.data.glances

import com.blayter.mantel.data.HttpStatusException
import com.blayter.mantel.data.httpGet
import com.blayter.mantel.domain.FoundMachine
import com.blayter.mantel.domain.GLANCES_PORT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/** What one address said when asked for Glances. */
sealed interface ProbeResult {
    data class Found(val machine: FoundMachine) : ProbeResult

    /** Nothing listening, or something that is not Glances. */
    data object NoAnswer : ProbeResult

    /** Glances is there but asks for a password, which Mantel cannot give yet. */
    data object NeedsPassword : ProbeResult
}

private const val CONNECT_TIMEOUT_MILLIS = 800
private const val ASK_AT_ONCE = 32

/**
 * Finds Glances on the home network during setup. It only ever opens a
 * connection to the Glances port and sends GET requests: it reads, and
 * changes nothing on any machine.
 *
 * @param portOpen whether anything is listening at an address and port.
 * @param get fetches one URL as text, throwing on any failure.
 */
class GlancesScanner(
    private val portOpen: suspend (String, Int) -> Boolean = ::tcpOpen,
    private val get: suspend (String) -> String = { httpGet(it) },
) {
    /** Asks one address. Never throws for a machine that is absent, silent or not Glances. */
    suspend fun probe(address: String, port: Int = GLANCES_PORT): ProbeResult {
        if (!portOpen(address, port)) return ProbeResult.NoAnswer
        return try {
            val snapshot = GlancesHostSource("http://$address:$port", get).snapshot()
            ProbeResult.Found(
                FoundMachine(
                    address = address,
                    port = port,
                    name = snapshot.host.name,
                    threads = snapshot.host.threads,
                    memoryGb = snapshot.host.memoryTotalGb,
                    containers = snapshot.containers.size,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpStatusException) {
            if (e.code == HttpURLConnection.HTTP_UNAUTHORIZED) ProbeResult.NeedsPassword else ProbeResult.NoAnswer
        } catch (e: Exception) {
            ProbeResult.NoAnswer
        }
    }

    /**
     * Asks every address, several at a time. [onChecked] is called with the
     * running count as each address is done, [onFound] for each machine that
     * answers; both on the caller's dispatcher.
     */
    suspend fun scan(
        addresses: List<String>,
        port: Int = GLANCES_PORT,
        onChecked: (Int) -> Unit,
        onFound: (FoundMachine) -> Unit,
    ) = coroutineScope {
        val gate = Semaphore(ASK_AT_ONCE)
        val checked = AtomicInteger(0)
        for (address in addresses) {
            launch {
                val result = gate.withPermit { probe(address, port) }
                if (result is ProbeResult.Found) onFound(result.machine)
                onChecked(checked.incrementAndGet())
            }
        }
    }
}

private suspend fun tcpOpen(address: String, port: Int): Boolean = withContext(Dispatchers.IO) {
    try {
        Socket().use { it.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS) }
        true
    } catch (e: Exception) {
        false
    }
}

/** The TV's own IPv4 address on the home network, or null when it has none. */
suspend fun ownNetworkAddress(): String? = withContext(Dispatchers.IO) {
    try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}
