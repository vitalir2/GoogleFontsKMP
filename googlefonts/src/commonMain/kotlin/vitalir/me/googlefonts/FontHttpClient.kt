package vitalir.me.googlefonts

/**
 * Minimal HTTP abstraction used by the library to download font files and the Google Fonts
 * directory.
 *
 * A zero-dependency JDK-backed implementation is used on the JVM by default. A Ktor-backed
 * implementation ships in the `googlefonts-ktor` module and can be injected via
 * [GoogleFonts.httpClient].
 */
public fun interface FontHttpClient {

    /**
     * Fetches [url] and returns the response body bytes.
     *
     * @throws GoogleFontException if the request fails or the response is not successful.
     */
    public suspend fun get(url: String): ByteArray
}