package vitalir.me.googlefonts

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fetches and caches the Google Fonts directory
 * (`https://fonts.gstatic.com/s/a/directory.xml`). The directory is only needed when a font file
 * is not already cached on disk. It is cached in memory for the process and on disk with a TTL,
 * so cold starts and offline URL resolution do not re-download it.
 *
 * The TTL is a refresh hint, not a hard expiry: when a refresh fetch fails, the stale on-disk
 * copy is used instead of failing, so an offline app can still resolve fonts it has not loaded
 * before. The network fetch and parse run outside the mutex (double-checked locking), so one
 * slow fetch does not serialize every font load behind the lock.
 */
internal object FontDirectoryProvider {

    internal const val DEFAULT_DIRECTORY_URL = "https://fonts.gstatic.com/s/a/directory.xml"

    /** Test hook: overrides the directory URL. */
    internal var directoryUrl: String = DEFAULT_DIRECTORY_URL

    private const val DISK_KEY = "directory.xml"
    private const val DISK_TIME_KEY = "directory.xml.time"
    private val ttl: Duration = 7.days
    private val ttlMillis: Long = ttl.inWholeMilliseconds

    private val mutex = Mutex()
    private var cached: FontDirectory? = null
    private var cachedAt: TimeSource.Monotonic.ValueTimeMark? = null

    suspend fun get(): FontDirectory {
        mutex.withLock {
            val current = cached
            val at = cachedAt
            if (current != null && at != null && at.elapsedNow() < ttl) return current
        }
        // Fetch and parse outside the lock; the last writer wins.
        val bytes = readDirectoryBytes()
        val directory = FontDirectoryParser.parse(bytes.decodeToString())
        mutex.withLock {
            cached = directory
            cachedAt = TimeSource.Monotonic.markNow()
        }
        return directory
    }

    /** Test hook: clears the in-memory directory cache. */
    internal suspend fun clear() {
        mutex.withLock {
            cached = null
            cachedAt = null
        }
    }

    private suspend fun readDirectoryBytes(): ByteArray {
        val disk = FontDiskCache.get(DISK_KEY)
        val meta = FontDiskCache.get(DISK_TIME_KEY)?.decodeToString()?.toLongOrNull()
        if (disk != null && meta != null && currentTimeMillis() - meta < ttlMillis) {
            return disk
        }
        val fetched = try {
            GoogleFonts.resolveHttpClient().get(GoogleFonts.directoryUrl ?: directoryUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleFontException) {
            if (disk != null) return disk
            throw e
        } catch (e: Exception) {
            // Stale directory is better than failure; otherwise uphold the error contract.
            val error = GoogleFontException("GET $directoryUrl failed", e)
            if (disk != null) return disk
            throw error
        }
        // Parse-then-cache: only persist XML that parses to a non-empty directory, so a
        // truncated or garbage response never poisons the disk cache.
        if (FontDirectoryParser.parse(fetched.decodeToString()).families.isEmpty()) {
            if (disk != null) return disk
            throw GoogleFontException("GET $directoryUrl returned an empty or malformed directory")
        }
        runCatching {
            FontDiskCache.put(DISK_KEY, fetched)
            FontDiskCache.put(DISK_TIME_KEY, currentTimeMillis().toString().encodeToByteArray())
        }
        return fetched
    }
}
