package vitalir.me.googlefonts

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.TimeSource

/**
 * Fetches and caches the Google Fonts directory
 * (`https://fonts.gstatic.com/s/a/directory.xml`). The directory is only needed when a font file
 * is not already cached on disk. It is cached in memory for the process and on disk with a TTL,
 * so cold starts and offline URL resolution do not re-download it.
 */
internal object FontDirectoryProvider {

    internal const val DEFAULT_DIRECTORY_URL = "https://fonts.gstatic.com/s/a/directory.xml"

    /** Test hook: overrides the directory URL. */
    internal var directoryUrl: String = DEFAULT_DIRECTORY_URL

    private const val DISK_KEY = "directory.xml"
    private const val DISK_TIME_KEY = "directory.xml.time"
    private val ttl: Duration = 7.days
    private val ttlMillis: Long = ttl.inWholeMilliseconds

    private var cached: FontDirectory? = null
    private var cachedAt: TimeSource.Monotonic.ValueTimeMark? = null

    suspend fun get(): FontDirectory {
        val current = cached
        val at = cachedAt
        if (current != null && at != null && at.elapsedNow() < ttl) return current
        val bytes = readDirectoryBytes()
        val directory = FontDirectoryParser.parse(bytes.decodeToString())
        cached = directory
        cachedAt = TimeSource.Monotonic.markNow()
        return directory
    }

    /** Test hook: clears the in-memory directory cache. */
    internal fun clear() {
        cached = null
        cachedAt = null
    }

    private suspend fun readDirectoryBytes(): ByteArray {
        val disk = FontDiskCache.get(DISK_KEY)
        val meta = FontDiskCache.get(DISK_TIME_KEY)?.decodeToString()?.toLongOrNull()
        if (disk != null && meta != null && currentTimeMillis() - meta < ttlMillis) {
            return disk
        }
        val fetched = GoogleFonts.resolveHttpClient().get(directoryUrl)
        FontDiskCache.put(DISK_KEY, fetched)
        FontDiskCache.put(DISK_TIME_KEY, currentTimeMillis().toString().encodeToByteArray())
        return fetched
    }
}