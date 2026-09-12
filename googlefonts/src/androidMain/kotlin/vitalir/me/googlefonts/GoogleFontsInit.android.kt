package vitalir.me.googlefonts

import android.content.Context

private var appContext: Context? = null

/**
 * Initializes the library with an application [context].
 *
 * On Android, [GoogleFont.load] and [GoogleFont.toFontFamily] need a `Context` to reach the
 * downloadable-fonts provider. [rememberGoogleFontFamily] obtains one from composition, so this
 * is only required when loading outside of composition.
 *
 * Call it once from `Application.onCreate`:
 *
 * ```kotlin
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         initializeGoogleFonts(this)
 *     }
 * }
 * ```
 *
 * @param context any context; the application context is retained.
 */
public fun initializeGoogleFonts(context: Context) {
    appContext = context.applicationContext
}

internal fun androidContext(context: Any?): Context? =
    (context as? Context) ?: appContext