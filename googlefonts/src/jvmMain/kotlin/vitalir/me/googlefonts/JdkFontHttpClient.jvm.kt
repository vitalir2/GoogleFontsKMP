package vitalir.me.googlefonts

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * Zero-dependency [FontHttpClient] backed by the JDK HTTP client. Used as the default on the JVM.
 *
 * The JDK client has no transparent decompression, so gzip is negotiated manually via
 * `Accept-Encoding` and decoded when the server responds with `Content-Encoding: gzip`.
 */
internal class JdkFontHttpClient : FontHttpClient {

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(DEFAULT_TIMEOUT)
        .build()

    override suspend fun get(url: String): ByteArray = runInterruptible(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "GoogleFontsKMP/0.1")
                .header("Accept-Encoding", "gzip")
                .timeout(DEFAULT_TIMEOUT)
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() !in 200..299) {
                throw GoogleFontException("GET $url failed with status ${response.statusCode()}")
            }
            if ("gzip".equals(response.headers().firstValue("Content-Encoding").orElse(null), ignoreCase = true)) {
                gunzip(response.body())
            } else {
                response.body()
            }
        } catch (e: GoogleFontException) {
            throw e
        } catch (e: Exception) {
            throw GoogleFontException("GET $url failed", e)
        }
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val output = ByteArrayOutputStream()
            input.copyTo(output)
            output.toByteArray()
        }

    private companion object {
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(15)
    }
}
