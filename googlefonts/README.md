# googlefonts

The core GoogleFontsKMP library: load Google Fonts at runtime in Compose Multiplatform — Android, iOS, and Desktop.

Coordinates: `me.vitalir:googlefonts`

## Quick start

```kotlin
import vitalir.me.googlefonts.GoogleFont
import vitalir.me.googlefonts.rememberGoogleFontFamily

@Composable
fun Greeting() {
    val roboto = rememberGoogleFontFamily(GoogleFont("Roboto"), weight = FontWeight.Bold)
    Text(
        text = "Hello, Google Fonts!",
        fontFamily = roboto, // null while loading / on failure
    )
}
```

## AndroidX-compatible API

The `Font(...)` factory mirrors the AndroidX `ui-text-google-fonts` API — same parameter names and
defaults, same provider constructor shape — so AndroidX call sites migrate by changing the import
only. Descriptors are distinct types; converting one is a one-liner:
`GoogleFont(androidXFont.name, androidXFont.bestEffort)`.

```kotlin
import vitalir.me.googlefonts.Font
import vitalir.me.googlefonts.GoogleFont
import androidx.compose.ui.text.font.FontFamily

val fontFamily = FontFamily(
    Font(googleFont = GoogleFont("Roboto"), weight = FontWeight.Bold)
)
```

## Platform behavior

- **Android** resolves fonts through the system downloadable-fonts provider (Google Play Services), exactly like the AndroidX library. Fonts are cached by the OS and work offline after the first load.
- **iOS / Desktop** download the font from the Google Fonts CDN, cache it on disk, and hand it to Compose as a `LoadedFont`. Offline after the first load.

## Installation

```kotlin
implementation("me.vitalir:googlefonts:0.1.0")
```

## Documentation

See the [root README](https://github.com/vitalir2/GoogleFontsKMP#readme) for the full guide, and the [API reference](https://vitalir2.github.io/GoogleFontsKMP/googlefonts/) for the complete API.