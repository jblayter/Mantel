package com.blayter.mantel.data.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/** What the display needs to know to go live. Comes from a pushed `mantel-config.json`. */
data class MantelConfig(
    /** Root of the Glances web server, without `/api/4`: `http://192.168.6.38:61208`. */
    val glancesUrl: String,
    /** Read-only GitHub token, or null when deploys are not set up. Never log or display it. */
    val githubToken: String? = null,
    /** Repositories whose workflow runs the deploys card shows, as `owner/name`. */
    val repos: List<String> = emptyList(),
) {
    /** The host part alone, for "Waiting for the first reading from ...". */
    val glancesHost: String get() = URI(glancesUrl).host

    /** Keeps the token out of logs and crash reports. */
    override fun toString() =
        "MantelConfig(glancesUrl=$glancesUrl, githubToken=${if (githubToken == null) "none" else "set"}, repos=$repos)"
}

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
    val raw = try {
        root["glancesUrl"]?.jsonPrimitive?.takeIf { it.isString }?.content?.trim()
    } catch (e: IllegalArgumentException) {
        null
    }
    if (raw.isNullOrEmpty()) return ConfigParseResult.Invalid("it has no \"glancesUrl\"")

    // Accept the address with or without the API path or a trailing slash.
    val trimmed = raw.trimEnd('/').removeSuffix("/api/4").trimEnd('/')
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        return ConfigParseResult.Invalid("\"glancesUrl\" must start with http://")
    }
    val uri = try {
        URI(trimmed)
    } catch (e: Exception) {
        return ConfigParseResult.Invalid("\"glancesUrl\" is not a web address")
    }
    if (uri.host.isNullOrEmpty()) return ConfigParseResult.Invalid("\"glancesUrl\" has no host")

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

    return ConfigParseResult.Ok(MantelConfig(trimmed, token, repos))
}

private val RepoName = Regex("""[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+""")
