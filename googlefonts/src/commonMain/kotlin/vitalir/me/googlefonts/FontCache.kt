package vitalir.me.googlefonts

import androidx.compose.ui.text.font.Font

/** In-process cache of resolved [Font] instances, keyed by the full font request. */
internal object FontMemoryCache {

    private val fonts = mutableMapOf<String, Font>()

    fun get(key: String): Font? = fonts[key]

    fun put(key: String, font: Font) {
        fonts[key] = font
    }

    fun clear() {
        fonts.clear()
    }
}

/** On-disk cache of downloaded font files, keyed by the resolved font file. */
internal object FontDiskCache {

    /** Test hook: overrides the platform cache directory. */
    internal var cacheDirOverride: String? = null

    private val dir: String?
        get() = cacheDirOverride ?: defaultCacheDir()

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
 * Stable, filesystem-safe key for a resolved font file. The same (name, weight, italic)
 * combination always maps to the same file, so offline loads never need the directory.
 */
internal fun fontFileKey(name: String, weight: Int, italic: Boolean): String {
    val safeName = name.lowercase().replace(Regex("[^a-z0-9]+"), "_")
    return "font_${safeName}_${weight}_${if (italic) "i" else "n"}.ttf"
}

/** Key for the in-memory font cache; includes variation settings since they affect rendering. */
internal fun fontMemoryKey(
    name: String,
    weight: Int,
    italic: Boolean,
    variation: String,
): String = "$name|$weight|$italic|$variation"