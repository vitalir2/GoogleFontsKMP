package vitalir.me.googlefonts

/**
 * Thrown when a Google Font cannot be resolved or loaded.
 *
 * Typical causes:
 * - the family or the requested weight/style is not present in the Google Fonts directory;
 * - a network request failed (no connection, non-2xx response);
 * - the Android downloadable-fonts provider rejected the request (provider not found, wrong
 *   certificates);
 * - no [FontHttpClient] is configured and the platform has no default;
 * - `initializeGoogleFonts` was not called before using [GoogleFont.load] outside composition on
 *   Android.
 *
 * @param message a human-readable description of the failure.
 * @param cause the underlying cause, when available.
 */
public class GoogleFontException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)