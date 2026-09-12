package vitalir.me.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun loadFontInternal(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
    context: Any?,
): Font {
    val androidContext = androidContext(context)
        ?: throw GoogleFontException(
            "No Android Context available. Call initializeGoogleFonts(context) or use rememberGoogleFont.",
        )
    val impl = createGoogleFont(googleFont, defaultGoogleFontProvider(), weight, style, variationSettings)
    val typeface = GoogleFontTypefaceLoader.awaitLoad(androidContext, impl as AndroidFont)
        ?: throw GoogleFontException(
            "Failed to load '${googleFont.name}' from the Google Play Services fonts provider",
        )
    return ResolvedTypefaceFont(typeface, weight, style, variationSettings)
}

internal actual fun defaultHttpClient(): FontHttpClient? = null

internal actual fun defaultCacheDir(): String? = null

internal actual suspend fun readFileOrNull(path: String): ByteArray? =
    withContext(ioDispatcher()) {
        val file = File(path)
        if (file.isFile) file.readBytes() else null
    }

internal actual suspend fun writeFile(path: String, bytes: ByteArray) {
    withContext(ioDispatcher()) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }
}

@Composable
internal actual fun getPlatformContext(): Any? = LocalContext.current

internal actual suspend fun isCachedInternal(
    googleFont: GoogleFont,
    weight: FontWeight,
    style: FontStyle,
): Boolean = false

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO