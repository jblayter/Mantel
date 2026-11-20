package com.blayter.mantel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The server answered, but not with 200. */
class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code from $url")

private const val TIMEOUT_MILLIS = 5000

/**
 * Fetches one URL as text. GET is the only request Mantel makes to a machine
 * or to the GitHub API: it is read-only. Throws on any failure; a non-200
 * answer is an [HttpStatusException].
 */
suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) throw HttpStatusException(code, url)
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/**
 * Posts a form and returns the answer as text. Used only to sign in to GitHub
 * (asking for a code, and exchanging it or a refresh token for a token): it
 * changes nothing in any repository or workflow. Never used for anything else.
 */
suspend fun httpPostForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val body = form.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw HttpStatusException(code, url)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
