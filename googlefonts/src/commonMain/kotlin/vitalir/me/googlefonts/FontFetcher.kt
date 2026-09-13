package vitalir.me.googlefonts

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.concurrent.Volatile
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

    /**
     * Returns the font file bytes, downloading and caching them if necessary. Concurrent calls
     * for the same font await the same in-flight download.
     */
    suspend fun fetch(
        googleFont: GoogleFont,
        weight: FontWeight,
        style: FontStyle,
    ): ByteArray {
        val key = fontFileKey(googleFont.name, weight.weight, style == FontStyle.Italic)
        mutex.withLock { memoryCache[key] }?.let { return it }
        FontDiskCache.get(key)?.let { bytes ->
            mutex.withLock { memoryCache[key] = bytes }
            return bytes
        }
        val deferred = CompletableDeferred<ByteArray>()
        val winner = mutex.withLock {
            inFlight[key]?.let { return@withLock it } ?: deferred.also { inFlight[key] = it }
        }
        if (winner !== deferred) return winner.await()
        try {
            val bytes = download(googleFont, weight, style)
            FontDiskCache.put(key, bytes)
            mutex.withLock { memoryCache[key] = bytes }
            deferred.complete(bytes)
            return bytes
        } catch (cause: Throwable) {
            deferred.completeExceptionally(cause)
            throw cause
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }
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
        return GoogleFonts.resolveHttpClient().get("https:" + entry.url)
    }
}