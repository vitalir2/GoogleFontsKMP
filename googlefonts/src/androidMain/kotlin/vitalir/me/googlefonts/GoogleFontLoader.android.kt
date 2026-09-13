package vitalir.me.googlefonts

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import androidx.core.provider.FontsContractCompat.FontRequestCallback
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Port of androidx `GoogleFontImpl`: an async [AndroidFont] resolved through the system
 * downloadable-fonts provider (Google Play Services).
 */
@OptIn(ExperimentalTextApi::class)
internal data class GoogleFontImpl(
    val name: String,
    private val fontProvider: GoogleFont.Provider,
    override val weight: FontWeight,
    override val style: FontStyle,
    val fontVariationSettings: FontVariation.Settings,
    val bestEffort: Boolean,
) : AndroidFont(FontLoadingStrategy.Async, GoogleFontTypefaceLoader, fontVariationSettings) {

    fun toFontRequest(context: Context): FontRequest {
        val query =
            "name=$name&weight=${weight.weight}" +
                "&italic=${style.toQueryParam()}&besteffort=${bestEffortQueryParam()}"
        val certs = fontProvider.certificates
        return if (certs != null) {
            FontRequest(
                fontProvider.providerAuthority,
                fontProvider.providerPackage,
                query,
                certs,
                variationSettings.toAndroidString(context),
            )
        } else {
            FontRequest(
                fontProvider.providerAuthority,
                fontProvider.providerPackage,
                query,
                fontProvider.certificatesRes,
                variationSettings.toAndroidString(context),
            )
        }
    }

    private fun bestEffortQueryParam() = if (bestEffort) "true" else "false"

    private fun FontStyle.toQueryParam(): Int = if (this == FontStyle.Italic) 1 else 0

    fun toTypefaceStyle(): Int {
        val isItalic = style == FontStyle.Italic
        val isBold = weight >= FontWeight.Bold
        return when {
            isItalic && isBold -> Typeface.BOLD_ITALIC
            isItalic -> Typeface.ITALIC
            isBold -> Typeface.BOLD
            else -> Typeface.NORMAL
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoogleFontImpl) return false
        if (name != other.name) return false
        if (fontProvider != other.fontProvider) return false
        if (weight != other.weight) return false
        if (style != other.style) return false
        if (bestEffort != other.bestEffort) return false
        if (fontVariationSettings != other.fontVariationSettings) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + fontProvider.hashCode()
        result = 31 * result + weight.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + bestEffort.hashCode()
        result = 31 * result + fontVariationSettings.hashCode()
        return result
    }

    override fun toString(): String {
        return "Font(GoogleFont(\"$name\", bestEffort=$bestEffort), weight=$weight, " +
            "style=$style, fontVariationSettings=$fontVariationSettings)"
    }
}

/** Default provider: Google Play Services fonts, with the well-known GMS signing certificates. */
internal actual fun defaultGoogleFontProvider(): GoogleFont.Provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = listOf(
        listOf(Base64.decode(GMS_CERT_DEV, Base64.DEFAULT)),
        listOf(Base64.decode(GMS_CERT_PROD, Base64.DEFAULT)),
    ),
)

/** Builds the async [AndroidFont] descriptor resolved by Compose's default resolver. */
internal actual fun createGoogleFont(
    googleFont: GoogleFont,
    fontProvider: GoogleFont.Provider,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
): Font = GoogleFontImpl(
    name = googleFont.name,
    fontProvider = fontProvider,
    weight = weight,
    style = style,
    fontVariationSettings = variationSettings.sortedByAxis(),
    bestEffort = googleFont.bestEffort,
)

@OptIn(ExperimentalTextApi::class)
internal object GoogleFontTypefaceLoader : AndroidFont.TypefaceLoader {

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
        error("GoogleFont only supports async loading: $font")
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? {
        require(font is GoogleFontImpl) { "Only GoogleFontImpl supported (actual $font)" }
        val fontRequest = font.toFontRequest(context)
        val typefaceStyle = font.toTypefaceStyle()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : FontRequestCallback() {
                override fun onTypefaceRetrieved(typeface: Typeface?) {
                    continuation.resume(
                        typeface.setFontVariationSettings(font.variationSettings, context),
                    )
                }

                override fun onTypefaceRequestFailed(reason: Int) {
                    // resumeWithException, not continuation.cancel: this library calls awaitLoad
                    // directly, and a CancellationException would silently cancel the caller's
                    // coroutine instead of surfacing the failure.
                    continuation.resumeWithException(
                        GoogleFontException(
                            "Failed to load $font (reason=$reason, ${reasonToString(reason)})",
                        ),
                    )
                }
            }

            FontsContractCompat.requestFont(
                context,
                fontRequest,
                typefaceStyle,
                false, /* isBlockingFetch */
                0, /* timeout - not used when isBlockingFetch=false */
                asyncHandlerForCurrentThreadOrMainIfNoLooper(),
                callback,
            )
        }
    }

    private fun asyncHandlerForCurrentThreadOrMainIfNoLooper(): Handler {
        val looper = Looper.myLooper() ?: Looper.getMainLooper()
        return HandlerHelper.createAsync(looper)
    }
}

internal object HandlerHelper {

    fun createAsync(looper: Looper): Handler {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Handler.createAsync(looper)
        } else {
            Handler(looper)
        }
    }
}

private fun reasonToString(reasonCode: Int): String {
    return when (reasonCode) {
        FontRequestCallback.FAIL_REASON_PROVIDER_NOT_FOUND ->
            "The requested provider was not found on this device."
        FontRequestCallback.FAIL_REASON_WRONG_CERTIFICATES ->
            "The given provider cannot be authenticated with the certificates given."
        FontRequestCallback.FAIL_REASON_FONT_LOAD_ERROR ->
            "Generic error loading font, for example variation settings were not parsable."
        FontRequestCallback.FAIL_REASON_FONT_NOT_FOUND ->
            "Font not found, please check availability on " +
                "https://fonts.gstatic.com/s/a/directory.xml"
        FontRequestCallback.FAIL_REASON_FONT_UNAVAILABLE ->
            "The provider found the queried font, but it is currently unavailable."
        FontRequestCallback.FAIL_REASON_MALFORMED_QUERY ->
            "The given query was not supported by this provider."
        FontRequestCallback.FAIL_REASON_SECURITY_VIOLATION ->
            "Font was not loaded due to security issues. This usually means the font was " +
                "attempted to load in a restricted context."
        else -> "Unknown error code"
    }
}

/**
 * A [Font] that wraps an already-resolved `Typeface`. Used to return a fully loaded font from
 * [GoogleFont.load] on Android.
 */
@OptIn(ExperimentalTextApi::class)
internal class ResolvedTypefaceFont(
    internal val typeface: Typeface,
    override val weight: FontWeight,
    override val style: FontStyle,
    variationSettings: FontVariation.Settings,
) : AndroidFont(FontLoadingStrategy.Blocking, ResolvedTypefaceLoader, variationSettings) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResolvedTypefaceFont) return false
        if (typeface != other.typeface) return false
        if (weight != other.weight) return false
        if (style != other.style) return false
        if (variationSettings != other.variationSettings) return false
        return true
    }

    override fun hashCode(): Int {
        var result = typeface.hashCode()
        result = 31 * result + weight.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + variationSettings.hashCode()
        return result
    }

    override fun toString(): String {
        return "Font(typeface=$typeface, weight=$weight, style=$style)"
    }
}

private object ResolvedTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? =
        (font as? ResolvedTypefaceFont)?.typeface

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? =
        (font as? ResolvedTypefaceFont)?.typeface
}

/**
 * Returns a sorted [FontVariation.Settings] by [FontVariation.Setting.axisName], so that the same
 * axes in different declaration orders produce the same cache identity.
 */
internal fun FontVariation.Settings.sortedByAxis(): FontVariation.Settings {
    if (settings.size <= 1) return this
    var needsSorting = false
    for (i in 0 until settings.size - 1) {
        if (settings[i].axisName > settings[i + 1].axisName) {
            needsSorting = true
            break
        }
    }
    if (!needsSorting) return this
    return FontVariation.Settings(*settings.sortedBy { it.axisName }.toTypedArray())
}