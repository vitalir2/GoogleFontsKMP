package vitalir.me.googlefonts

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithURL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Zero-dependency [FontHttpClient] backed by `NSURLSession`. Used as the default on iOS.
 *
 * Uses an explicit session configuration with a request timeout, instead of `sharedSession`
 * (whose 60s default could block callers for a minute).
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosFontHttpClient : FontHttpClient {

    private val session: NSURLSession = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.defaultSessionConfiguration.apply {
            setTimeoutIntervalForRequest(REQUEST_TIMEOUT_SECONDS)
        },
    )

    override suspend fun get(url: String): ByteArray = suspendCancellableCoroutine { continuation ->
        val nsUrl = NSURL(string = url)
        val task = session.dataTaskWithURL(nsUrl) { data, response, error ->
            if (error != null) {
                continuation.resumeWithException(
                    GoogleFontException("GET $url failed: ${error.localizedDescription}"),
                )
                return@dataTaskWithURL
            }
            val httpResponse = response as? NSHTTPURLResponse
            if (httpResponse != null && httpResponse.statusCode !in 200..299) {
                continuation.resumeWithException(
                    GoogleFontException("GET $url failed with status ${httpResponse.statusCode}"),
                )
                return@dataTaskWithURL
            }
            continuation.resume(data?.toByteArray() ?: byteArrayOf())
        }
        task.resume()
        continuation.invokeOnCancellation { task.cancel() }
    }

    private companion object {
        private const val REQUEST_TIMEOUT_SECONDS = 15.0
    }
}