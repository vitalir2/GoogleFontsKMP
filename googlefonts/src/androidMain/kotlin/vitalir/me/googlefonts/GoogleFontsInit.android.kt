package vitalir.me.googlefonts

import android.content.Context

private var appContext: Context? = null

/**
 * Initializes the library with an application [context]. Required for [GoogleFont.load] outside
 * of composition on Android. [rememberGoogleFont] does not require initialization because it
 * obtains the context from the composition.
 */
public fun initializeGoogleFonts(context: Context) {
    appContext = context.applicationContext
}

internal fun androidContext(context: Any?): Context? =
    (context as? Context) ?: appContext