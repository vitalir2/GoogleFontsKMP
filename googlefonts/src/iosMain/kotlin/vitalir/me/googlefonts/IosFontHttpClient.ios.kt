package vitalir.me.googlefonts

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Zero-dependency [FontHttpClient] backed by `NSURLSession`. Used as the default on iOS.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosFontHttpClient : FontHttpClient {

    private val session = NSURLSession.sharedSession

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
}