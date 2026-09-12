package vitalir.me.googlefonts

/** Thrown when a Google Font cannot be resolved or loaded. */
public class GoogleFontException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)