package com.blayter.mantel.data.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URI

/** What the display needs to know to go live. Comes from a pushed `mantel-config.json`. */
data class MantelConfig(
    /** Root of the Glances web server, without `/api/4`: `http://192.168.6.38:61208`. */
    val glancesUrl: String,
    /** Read-only GitHub token, or null when deploys are not set up. Never log or display it. */
    val githubToken: String? = null,
    /** Repositories whose workflow runs the deploys card shows, as `owner/name`. */
    val repos: List<String> = emptyList(),
    /** Further machines to watch, after the first. The display takes turns between them. */
    val moreGlancesUrls: List<String> = emptyList(),
) {
    /** Every machine to watch, the first one first. */
    val glancesUrls: List<String> get() = listOf(glancesUrl) + moreGlancesUrls

    /** The host part alone, for "Waiting for the first reading from ...". */
    val glancesHost: String get() = hostOf(glancesUrl)

    /** Keeps the token out of logs and crash reports. */
    override fun toString() =
        "MantelConfig(glancesUrls=$glancesUrls, githubToken=${if (githubToken == null) "none" else "set"}, repos=$repos)"
}

/** The host part of a Glances address: `192.168.6.38`. */
fun hostOf(glancesUrl: String): String = URI(glancesUrl).host

sealed interface ConfigParseResult {
    data class Ok(val config: MantelConfig) : ConfigParseResult

    /** [reason] is a plain sentence fragment that fits after "The config file could not be used: ". */
    data class Invalid(val reason: String) : ConfigParseResult
}

/**
 * Reads the text of `mantel-config.json`:
 *
 *     {
 *       "glancesUrl": "http://192.168.6.38:61208",
 *       "githubToken": "github_pat_...",
 *       "repos": ["example-org/RxFlow"]
 *     }
 *
 * To watch several machines, give `"glancesUrls": ["http://...", "http://..."]`
 * instead of `glancesUrl`; the display takes turns between them, and the first
 * one is the CI box whose runners are expected to be present.
 *
 * `githubToken` and `repos` go together and are both optional; without them
 * the deploys card is not live. A pushed file replaces the whole stored
 * config, so it must always be complete. Unknown keys are ignored, so a file written for a later version still works.
 */
fun parseConfig(text: String): ConfigParseResult {
    val root = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (e: Exception) {
        return ConfigParseResult.Invalid("it is not valid JSON")
    }
    val addresses: List<String> = when (val list = root["glancesUrls"]) {
        null, JsonNull -> {
            val one = (root["glancesUrl"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (one.isNullOrBlank()) return ConfigParseResult.Invalid("it has no \"glancesUrl\"")
            listOf(one)
        }
        is JsonArray -> {
            if (root["glancesUrl"] != null) return ConfigParseResult.Invalid("it has both \"glancesUrl\" and \"glancesUrls\"")
            val all = list.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            if (all.isEmpty() || all.any { it.isNullOrBlank() }) {
                return ConfigParseResult.Invalid("\"glancesUrls\" must be a list of addresses in quotes")
            }
            all.filterNotNull()
        }
        else -> return ConfigParseResult.Invalid("\"glancesUrls\" must be a list in [ ]")
    }
    val key = if (root["glancesUrls"] is JsonArray) "glancesUrls" else "glancesUrl"
    val urls = addresses.map { raw ->
        // Accept the address with or without the API path or a trailing slash.
        val trimmed = raw.trim().trimEnd('/').removeSuffix("/api/4").trimEnd('/')
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return ConfigParseResult.Invalid("\"$key\" must start with http://")
        }
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return ConfigParseResult.Invalid("\"$key\" is not a web address")
        }
        if (uri.host.isNullOrEmpty()) return ConfigParseResult.Invalid("\"$key\" has no host")
        trimmed
    }.distinct()

    val token = when (val element = root["githubToken"]) {
        null, JsonNull -> null
        is JsonPrimitive -> element.takeIf { it.isString }?.content?.trim()
            ?: return ConfigParseResult.Invalid("\"githubToken\" must be text in quotes")
        else -> return ConfigParseResult.Invalid("\"githubToken\" must be text in quotes")
    }?.takeIf { it.isNotEmpty() }

    val repos = when (val element = root["repos"]) {
        null, JsonNull -> emptyList()
        is JsonArray -> element.map {
            val name = (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()
            if (name == null || !RepoName.matches(name)) {
                return ConfigParseResult.Invalid("each of \"repos\" must look like \"owner/name\"")
            }
            name
        }
        else -> return ConfigParseResult.Invalid("\"repos\" must be a list in [ ]")
    }
    if (token != null && repos.isEmpty()) return ConfigParseResult.Invalid("it has a \"githubToken\" but no \"repos\"")
    if (token == null && repos.isNotEmpty()) return ConfigParseResult.Invalid("it has \"repos\" but no \"githubToken\"")

    return ConfigParseResult.Ok(MantelConfig(urls.first(), token, repos, urls.drop(1)))
}

private val RepoName = Regex("""[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+""")
