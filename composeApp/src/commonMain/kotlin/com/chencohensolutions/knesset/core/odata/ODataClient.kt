package com.chencohensolutions.knesset.core.odata

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json

/**
 * The only class that talks to the network. Low concurrency, bounded retries, and every failure is
 * mapped to a [KnessetError].
 */
class ODataClient(
    private val http: HttpClient,
    maxInFlight: Int = 2,
    private val retryDelaysMs: List<Long> = listOf(1_000, 3_000),
) {
    private val limiter = Semaphore(maxInFlight)

    /** GETs [url] and returns the JSON body as text. */
    suspend fun getText(url: String): String {
        var attempt = 0
        while (true) {
            try {
                return limiter.withPermit { fetchOnce(url) }
            } catch (e: Retryable) {
                if (attempt >= retryDelaysMs.size) throw e.error
                delay(e.retryAfterMs ?: retryDelaysMs[attempt])
                attempt++
            }
        }
    }

    private class Retryable(val error: KnessetError, val retryAfterMs: Long? = null) : Exception()

    private suspend fun fetchOnce(url: String): String {
        val response = try {
            http.get(url) { header(HttpHeaders.Accept, "application/json") }
        } catch (e: HttpRequestTimeoutException) {
            throw Retryable(KnessetError.Offline(e))
        } catch (e: IOException) {
            // Connect and socket timeouts are worth a retry; other IO failures mean no network.
            if (e::class.simpleName?.contains("Timeout") == true) throw Retryable(KnessetError.Offline(e))
            throw KnessetError.Offline(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Engines report connection problems with their own exception types.
            throw KnessetError.Offline(e)
        }
        val code = response.status.value
        when {
            code == HttpStatusCode.TooManyRequests.value || code == 502 || code == 503 || code == 504 -> {
                val retryAfter = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.times(1_000)
                throw Retryable(if (code == 429 || code == 503) KnessetError.RateLimited() else KnessetError.Server(code), retryAfter)
            }
            // The server answers malformed or unsupported queries with 500, so a retry would not help.
            code == 404 -> throw KnessetError.NotFound()
            code !in 200..299 -> throw KnessetError.Server(code)
        }
        val body = response.bodyAsText()
        val type = response.contentType()
        val looksJson = type?.contentSubtype?.contains("json", ignoreCase = true) == true ||
            (type == null && body.trimStart().startsWith("{"))
        if (!looksJson || body.trimStart().startsWith("<")) throw KnessetError.BlockedOrUnavailable()
        return body
    }

    companion object {
        const val USER_AGENT = "KnessetApp"

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
            isLenient = true
        }

        /** Shared HTTP configuration; the platform supplies the engine. */
        fun configure(client: HttpClient, appVersion: String): HttpClient = client.config {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
            defaultRequest { header(HttpHeaders.UserAgent, "$USER_AGENT/$appVersion") }
        }
    }
}
