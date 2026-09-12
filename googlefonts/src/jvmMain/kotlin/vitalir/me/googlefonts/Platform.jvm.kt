package vitalir.me.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.io.File

internal actual suspend fun loadFontInternal(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
    context: Any?,
): Font {
    val italic = style == FontStyle.Italic
    val key = fontFileKey(googleFont.name, weight.weight, italic)
    val bytes = FontDiskCache.get(key) ?: run {
        val directory = FontDirectoryProvider.get()
        val entry = directory.resolve(googleFont.name, weight.weight, italic, googleFont.bestEffort)
            ?: throw GoogleFontException(
                "Font '${googleFont.name}' (weight=${weight.weight}, italic=$italic) " +
                    "not found in the Google Fonts directory",
            )
        val fetched = GoogleFonts.resolveHttpClient().get("https:" + entry.url)
        FontDiskCache.put(key, fetched)
        fetched
    }
    return createLoadedFont(key, bytes, weight, style)
}

@OptIn(InternalComposeUiApi::class)
private fun createLoadedFont(
    key: String,
    bytes: ByteArray,
    weight: FontWeight,
    style: FontStyle,
): Font = Font(
    identity = "googlefonts:$key",
    data = bytes,
    weight = weight,
    style = style,
)

internal actual fun defaultHttpClient(): FontHttpClient? = JdkFontHttpClient()

internal actual fun defaultCacheDir(): String? {
    val home = System.getProperty("user.home")
    if (!home.isNullOrBlank()) return "$home/.cache/googlefonts"
    return System.getProperty("java.io.tmpdir")?.let { "$it/googlefonts" }
}

internal actual suspend fun readFileOrNull(path: String): ByteArray? {
    val file = File(path)
    return if (file.isFile) file.readBytes() else null
}

internal actual suspend fun writeFile(path: String, bytes: ByteArray) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeBytes(bytes)
}

@Composable
internal actual fun rememberPlatformContext(): Any? = null

internal actual suspend fun isCachedInternal(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
): Boolean = FontDiskCache.get(fontFileKey(googleFont.name, weight.weight, style == FontStyle.Italic)) != null