package com.blayter.mantel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The server answered, but not with 200. */
class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code from $url")

private const val TIMEOUT_MILLIS = 5000

/**
 * Fetches one URL as text. GET is the only request Mantel ever makes: it is
 * read-only. Throws on any failure; a non-200 answer is an [HttpStatusException].
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
