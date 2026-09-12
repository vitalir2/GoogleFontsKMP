package vitalir.me.googlefonts

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.TimeSource

/**
 * Fetches and caches the Google Fonts directory
 * (`https://fonts.gstatic.com/s/a/directory.xml`). The directory is only needed when a font file
 * is not already cached on disk.
 */
internal object FontDirectoryProvider {

    internal const val DEFAULT_DIRECTORY_URL = "https://fonts.gstatic.com/s/a/directory.xml"

    /** Test hook: overrides the directory URL. */
    internal var directoryUrl: String = DEFAULT_DIRECTORY_URL

    private val ttl: Duration = 7.days

    private var cached: FontDirectory? = null
    private var cachedAt: TimeSource.Monotonic.ValueTimeMark? = null

    suspend fun get(): FontDirectory {
        val current = cached
        val at = cachedAt
        if (current != null && at != null && at.elapsedNow() < ttl) return current
        val bytes = GoogleFonts.resolveHttpClient().get(directoryUrl)
        val directory = FontDirectoryParser.parse(bytes.decodeToString())
        cached = directory
        cachedAt = TimeSource.Monotonic.markNow()
        return directory
    }
}