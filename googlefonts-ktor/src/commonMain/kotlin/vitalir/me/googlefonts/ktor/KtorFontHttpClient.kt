package vitalir.me.googlefonts.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import vitalir.me.googlefonts.FontHttpClient
import vitalir.me.googlefonts.GoogleFontException

/**
 * [FontHttpClient] backed by a Ktor [HttpClient].
 *
 * ```kotlin
 * GoogleFonts.httpClient = KtorFontHttpClient(HttpClient())
 * ```
 *
 * @param client the Ktor client to use. The library does not close it — manage its lifecycle
 *   yourself.
 */
public class KtorFontHttpClient(
    private val client: HttpClient,
) : FontHttpClient {

    /**
     * Fetches [url] and returns the response body bytes.
     *
     * @throws GoogleFontException if the request fails or the response is not successful.
     */
    override suspend fun get(url: String): ByteArray = try {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            throw GoogleFontException("GET $url failed with status ${response.status.value}")
        }
        response.bodyAsBytes()
    } catch (e: GoogleFontException) {
        throw e
    } catch (e: Exception) {
        throw GoogleFontException("GET $url failed", e)
    }
}