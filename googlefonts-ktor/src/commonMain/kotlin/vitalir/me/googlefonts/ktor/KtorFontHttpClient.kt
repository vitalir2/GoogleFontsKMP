package vitalir.me.googlefonts.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import vitalir.me.googlefonts.FontHttpClient
import vitalir.me.googlefonts.GoogleFontException
import vitalir.me.googlefonts.GoogleFonts

/**
 * [FontHttpClient] backed by a Ktor [HttpClient].
 *
 * ```
 * GoogleFonts.httpClient = KtorFontHttpClient(HttpClient())
 * ```
 */
public class KtorFontHttpClient(
    private val client: HttpClient,
) : FontHttpClient {

    override suspend fun get(url: String): ByteArray {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            throw GoogleFontException("GET $url failed with status ${response.status.value}")
        }
        return response.bodyAsBytes()
    }
}

/** Configures [GoogleFonts] to use the given Ktor [HttpClient]. */
public fun GoogleFonts.useKtorClient(client: HttpClient) {
    httpClient = KtorFontHttpClient(client)
}