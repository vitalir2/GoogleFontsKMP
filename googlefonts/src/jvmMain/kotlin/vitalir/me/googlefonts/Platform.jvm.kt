package vitalir.me.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal actual suspend fun loadFontInternal(
    googleFont: GoogleFont,
    fontProvider: GoogleFont.Provider,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
    context: Any?,
): Font {
    FontFetcher.fetch(googleFont, weight, style) // eager: warm the cache before returning
    return buildLoadedFont(googleFont, weight, style, variationSettings)
}

internal actual fun createGoogleFont(
    googleFont: GoogleFont,
    fontProvider: GoogleFont.Provider,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
): Font {
    // Prefetch during composition so the first layout usually hits the cache.
    FontFetcher.fetchAsync(googleFont, weight, style)
    return buildLoadedFont(googleFont, weight, style, variationSettings)
}

private fun buildLoadedFont(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
): Font {
    val key = fontFileKey(googleFont.name, weight.weight, style == FontStyle.Italic, googleFont.bestEffort)
    return Font(
        identity = "googlefonts:$key",
        getData = {
            runBlocking {
                try {
                    FontFetcher.fetch(googleFont, weight, style)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Exception-safe: a failed download must never crash composition. Serve the
                    // last successfully fetched bytes when available, otherwise rethrow.
                    FontFetcher.cachedBytes(key) ?: throw e
                }
            }
        },
        weight = weight,
        style = style,
        variationSettings = variationSettings,
    )
}

internal actual fun defaultGoogleFontProvider(): GoogleFont.Provider =
    GoogleFont.Provider("", "", emptyList())

internal actual fun defaultHttpClient(): FontHttpClient? = JdkFontHttpClient()

internal actual fun defaultCacheDir(): String? {
    // Honor XDG_CACHE_HOME when set, falling back to ~/.cache.
    val xdgCache = System.getenv("XDG_CACHE_HOME")
    if (!xdgCache.isNullOrBlank()) return "$xdgCache/googlefonts"
    val home = System.getProperty("user.home")
    if (!home.isNullOrBlank()) return "$home/.cache/googlefonts"
    return System.getProperty("java.io.tmpdir")?.let { "$it/googlefonts" }
}

internal actual suspend fun readFileOrNull(path: String): ByteArray? =
    withContext(ioDispatcher()) {
        val file = File(path)
        if (file.isFile) file.readBytes() else null
    }

internal actual suspend fun writeFile(path: String, bytes: ByteArray) {
    withContext(ioDispatcher()) {
        val file = File(path)
        file.parentFile?.mkdirs()
        // Write to a unique temp file and move atomically, so a crash mid-write can never leave
        // a truncated file that would be served from the cache forever, and concurrent writers
        // of the same path don't race on a shared temp name.
        val tmp = File("$path.tmp${System.nanoTime()}")
        tmp.writeBytes(bytes)
        try {
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }
}

@Composable
internal actual fun getPlatformContext(): Any? = null

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO