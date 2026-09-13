package vitalir.me.googlefonts

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-process cache of resolved [Font] instances, keyed by the full font request. */
internal object FontMemoryCache {

    private val mutex = Mutex()
    private val fonts = mutableMapOf<String, Font>()

    suspend fun get(key: String): Font? = mutex.withLock { fonts[key] }

    suspend fun put(key: String, font: Font) {
        mutex.withLock { fonts[key] = font }
    }

    suspend fun clear() {
        mutex.withLock { fonts.clear() }
    }
}

// TODO make concurrent safe
/** On-disk cache of downloaded font files, keyed by the resolved font file. */
internal object FontDiskCache {

    // TODO permit to override the cache dir in the settings for the library.
    /** Test hook: overrides the platform cache directory. */
    internal var cacheDirOverride: String? = null

    private val dir: String?
        get() = GoogleFonts.cacheDir ?: cacheDirOverride ?: defaultCacheDir()

    suspend fun get(key: String): ByteArray? {
        val d = dir ?: return null
        return readFileOrNull("$d/$key")
    }

    suspend fun put(key: String, bytes: ByteArray) {
        val d = dir ?: return
        writeFile("$d/$key", bytes)
    }
}

/**
 * Stable, filesystem-safe key for a resolved font file. The same (name, weight, italic, bestEffort)
 * combination always maps to the same file, so offline loads never need the directory.
 *
 * `bestEffort` is part of the key: a file fetched as a closest match must not be served to a
 * strict request that should have failed, and strict/bestEffort requests must not overwrite each
 * other's cache entries.
 */
internal fun fontFileKey(name: String, weight: Int, italic: Boolean, bestEffort: Boolean): String {
    val safeName = name.lowercase().replace(Regex("[^a-z0-9]+"), "_")
    return "font_${safeName}_${weight}_${if (italic) "i" else "n"}_${if (bestEffort) "b" else "s"}.ttf"
}

/** Key for the in-memory font cache; includes variation settings since they affect rendering. */
internal fun fontMemoryKey(
    name: String,
    weight: Int,
    italic: Boolean,
    variation: String,
): String = "$name|$weight|$italic|$variation"

/**
 * Order-insensitive cache key for variation settings, so the same axes declared in a different
 * order produce the same key (mirrors Android's `sortedByAxis()` normalization).
 */
internal fun variationCacheKey(settings: FontVariation.Settings): String =
    settings.settings
        .sortedBy { it.axisName }
        .joinToString(",") { "${it.axisName}=${it.toString()}" }

/**
 * Cheap sanity check that [bytes] look like a real font file. Guards the disk cache against
 * caching non-font bodies (for example a captive-portal HTML page served with status 200).
 */
internal fun isPlausibleFontFile(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    val magic = ((bytes[0].toInt() and 0xFF) shl 24) or
        ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or
        (bytes[3].toInt() and 0xFF)
    return when (magic) {
        0x00010000, // TTF
        0x4F54544F, // "OTTO" (OpenType)
        0x74746366, // "ttcf" (TrueType collection)
        0x74727565, // "true"
        0x774F4646, // "wOFF"
        -> true
        else -> false
    }
}