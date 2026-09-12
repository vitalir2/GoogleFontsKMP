package vitalir.me.googlefonts

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * Zero-dependency [FontHttpClient] backed by the JDK HTTP client. Used as the default on the JVM.
 */
internal class JdkFontHttpClient : FontHttpClient {

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override suspend fun get(url: String): ByteArray = runInterruptible(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "GoogleFontsKMP/0.1")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            throw GoogleFontException("GET $url failed with status ${response.statusCode()}")
        }
        response.body()
    }
}