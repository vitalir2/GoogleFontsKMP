package vitalir.me.googlefonts

/**
 * Minimal HTTP abstraction used by the library to download font files and the Google Fonts
 * directory.
 *
 * A zero-dependency JDK-backed implementation is used on the JVM by default, and an
 * `NSURLSession`-backed one on iOS. A Ktor-backed implementation ships in the `googlefonts-ktor`
 * module and can be injected via [GoogleFonts.httpClient].
 *
 * Implementations must be safe to call from multiple coroutines concurrently, since fonts may be
 * downloaded in parallel.
 *
 * ```kotlin
 * val client = FontHttpClient { url -> myCache.getOrFetch(url) }
 * ```
 */
public fun interface FontHttpClient {

    /**
     * Fetches [url] and returns the response body bytes.
     *
     * @param url the absolute URL to fetch.
     * @throws GoogleFontException if the request fails or the response is not successful.
     */
    public suspend fun get(url: String): ByteArray
}
