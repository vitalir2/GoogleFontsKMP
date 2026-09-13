package vitalir.me.googlefonts

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deduplicated font download pipeline. All load paths — [GoogleFont.load], [GoogleFont.warmUp],
 * the `Font(...)` factory prefetch, and the skiko `getData` — share a single in-flight download
 * per font file, so concurrent requests never double-fetch.
 */
internal object FontFetcher {

    @Volatile
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<ByteArray>>()

    /** In-memory byte cache so repeated skiko `getData` calls skip the disk read. */
    private val memoryCache = mutableMapOf<String, ByteArray>()

    /** Test hook: cancels in-flight background fetches and clears the caches. */
    internal fun reset() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        runBlocking {
            mutex.withLock {
                inFlight.clear()
                memoryCache.clear()
            }
        }
    }

    /** Test hook: suspends until all background prefetches have finished. */
    internal suspend fun awaitIdle() {
        scope.coroutineContext[Job]?.children?.toList()?.joinAll()
    }

    /** Returns previously fetched bytes for [key], or null when nothing was fetched yet. */
    internal suspend fun cachedBytes(key: String): ByteArray? = mutex.withLock { memoryCache[key] }

    /**
     * Returns the font file bytes, downloading and caching them if necessary. Concurrent calls
     * for the same font await the same in-flight download.
     *
     * The download runs in [FontFetcher]'s own scope, so a caller being cancelled mid-download
     * cannot poison the shared deferred for other concurrent waiters.
     */
    suspend fun fetch(
        googleFont: GoogleFont,
        weight: FontWeight,
        style: FontStyle,
    ): ByteArray {
        val key = fontFileKey(googleFont.name, weight.weight, style == FontStyle.Italic, googleFont.bestEffort)
        mutex.withLock { memoryCache[key] }?.let { return it }
        FontDiskCache.get(key)?.let { bytes ->
            // Validate cached bytes too: a pre-existing truncated/corrupt file must not be
            // served forever.
            if (isPlausibleFontFile(bytes)) {
                mutex.withLock { memoryCache[key] = bytes }
                return bytes
            }
        }
        val deferred = CompletableDeferred<ByteArray>()
        val winner = mutex.withLock {
            inFlight[key]?.let { return@withLock it } ?: deferred.also { inFlight[key] = it }
        }
        if (winner !== deferred) return winner.await()
        // Run the download in FontFetcher's own scope: the deferred must be independent of any
        // single caller, so one cancelled caller cannot fail every concurrent waiter.
        scope.launch {
            try {
                val bytes = download(googleFont, weight, style)
                if (!isPlausibleFontFile(bytes)) {
                    throw GoogleFontException(
                        "GET for '${googleFont.name}' returned a body that is not a font file",
                    )
                }
                // A disk-cache write failure must not fail the load; memory cache still works.
                runCatching { FontDiskCache.put(key, bytes) }
                mutex.withLock { memoryCache[key] = bytes }
                deferred.complete(bytes)
            } catch (cause: Throwable) {
                deferred.completeExceptionally(cause)
            } finally {
                mutex.withLock { inFlight.remove(key) }
            }
        }
        return deferred.await()
    }

    /** Starts a background fetch without suspending the caller. */
    fun fetchAsync(
        googleFont: GoogleFont,
        weight: FontWeight,
        style: FontStyle,
    ) {
        scope.launch { runCatching { fetch(googleFont, weight, style) } }
    }

    private suspend fun download(
        googleFont: GoogleFont,
        weight: FontWeight,
        style: FontStyle,
    ): ByteArray {
        val directory = FontDirectoryProvider.get()
        val italic = style == FontStyle.Italic
        val entry = directory.resolve(googleFont.name, weight.weight, italic, googleFont.bestEffort)
            ?: throw GoogleFontException(
                "Font '${googleFont.name}' (weight=${weight.weight}, italic=$italic) " +
                    "not found in the Google Fonts directory",
            )
        // Chokepoint: wrap any non-library exception (raw IOException from a custom client, etc.)
        // so the documented error contract holds for every load path.
        return try {
            GoogleFonts.resolveHttpClient().get("https:" + entry.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e as? GoogleFontException ?: GoogleFontException(
                "GET ${"https:" + entry.url} failed",
                e,
            )
        }
    }
}