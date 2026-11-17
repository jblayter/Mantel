package com.blayter.mantel.data.glances

import com.blayter.mantel.data.HostSnapshot
import com.blayter.mantel.data.RawContainer
import com.blayter.mantel.domain.HostInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Turns Glances 4 REST replies into a [HostSnapshot]. Pure, so it is tested
 * against replies captured from the real CI box.
 *
 * Memory is in binary units throughout (1 GB = 1024 MB = 2^30 bytes), which
 * is what Glances and `docker stats` display: a `4g` cap reads as 4.0 GB and
 * a 64 GB machine as 59 GB.
 */
object GlancesParser {
    private const val BYTES_PER_MB = 1024.0 * 1024.0
    private const val BYTES_PER_GB = BYTES_PER_MB * 1024.0

    /** Statuses of containers that are not running; they get no cell. */
    private val NotRunning = setOf("exited", "created", "dead", "removing", "stopped")

    /** `/system`: the box's own name, without any domain. */
    fun hostName(system: String): String =
        Json.parseToJsonElement(system).jsonObject.string("hostname")?.substringBefore('.')?.takeIf { it.isNotEmpty() }
            ?: error("no hostname in /system")

    fun snapshot(hostName: String, containers: String, quicklook: String, mem: String, load: String): HostSnapshot {
        val quick = Json.parseToJsonElement(quicklook).jsonObject
        val memory = Json.parseToJsonElement(mem).jsonObject
        val loads = Json.parseToJsonElement(load).jsonObject

        val totalBytes = memory.number("total") ?: error("no total in /mem")
        val threads = loads.number("cpucore")?.toInt()
            ?: (quick["percpu"] as? JsonArray)?.size
            ?: error("no core count in /load or /quicklook")

        return HostSnapshot(
            host = HostInfo(hostName, threads, (totalBytes / BYTES_PER_GB).toInt()),
            memoryTotalMb = (totalBytes / BYTES_PER_MB).roundToInt(),
            containers = containers(containers),
            cpuPercent = (quick.number("cpu") ?: error("no cpu in /quicklook")).toFloat(),
            memoryUsedGb = ((memory.number("used") ?: 0.0) / BYTES_PER_GB).toFloat(),
            load1 = (loads.number("min1") ?: 0.0).toFloat(),
            load5 = (loads.number("min5") ?: 0.0).toFloat(),
            load15 = (loads.number("min15") ?: 0.0).toFloat(),
        )
    }

    /** `/containers`: a list, or something else (an empty object) when there are none. */
    fun containers(text: String): List<RawContainer> {
        val list = Json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val c = element as? JsonObject ?: return@mapNotNull null
            val name = c.string("name") ?: return@mapNotNull null
            if (c.string("status")?.lowercase() in NotRunning) return@mapNotNull null
            RawContainer(
                name = name,
                image = image(c["image"]),
                cpuPercent = c.number("cpu_percent") ?: 0.0,
                memoryMb = ((c.number("memory_usage") ?: 0.0) / BYTES_PER_MB).roundToInt(),
                memoryLimitMb = ((c.number("memory_limit") ?: 0.0) / BYTES_PER_MB).roundToInt(),
                uptime = uptime(c.string("uptime").orEmpty()),
            )
        }
    }

    /** Glances 4 reports `image` as a list of tags; older versions as one string. */
    private fun image(element: JsonElement?): String = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotEmpty) }.orEmpty()
        is JsonPrimitive -> if (element.isString) element.content else ""
        else -> ""
    }

    private val UptimeWords = Regex("""^(an?|\d+)\s+(sec|min|hour|day|week|month|year)""")

    /**
     * Glances gives uptime only as rounded words: "6 hours", "a week",
     * "yesterday", "just now". This is as exact as the cell's age can be.
     * Anything unrecognised reads as zero.
     */
    fun uptime(words: String): Duration {
        val text = words.trim().lowercase()
        if (text == "yesterday") return 1.days
        val match = UptimeWords.find(text) ?: return Duration.ZERO
        val n = match.groupValues[1].toIntOrNull() ?: 1
        return when (match.groupValues[2]) {
            "sec" -> n.seconds
            "min" -> n.minutes
            "hour" -> n.hours
            "day" -> n.days
            "week" -> (n * 7).days
            "month" -> (n * 30).days
            else -> (n * 365).days
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
}
