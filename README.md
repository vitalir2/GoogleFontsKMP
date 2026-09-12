# GoogleFontsKMP

Load [Google Fonts](https://fonts.google.com) at runtime in Compose Multiplatform — Android, iOS,
Desktop (JVM) and Web (JS/Wasm, planned). A Kotlin Multiplatform port of the AndroidX
`ui-text-google-fonts` downloadable-fonts integration.

- **Android** uses the system downloadable-fonts provider (Google Play Services) via
  `FontsContractCompat`, exactly like the AndroidX library — fonts are cached by the OS and work
  offline after the first load.
- **iOS / Desktop** download the font from the Google Fonts CDN, cache it on disk, and hand it to
  Compose as a `LoadedFont`. Offline after the first load.
- **Web** (planned) will use URL-based fonts and the browser cache.

## Requirements

- Kotlin 2.4+, Compose Multiplatform 1.11+
- JDK 17+ (21 recommended)
- Android: minSdk 29, compileSdk 37

## Modules

| Module | Description |
|---|---|
| `googlefonts` | The library. Common API + Android/iOS/Desktop implementations. |
| `googlefonts-ktor` | Optional Ktor-backed `FontHttpClient` implementation. |
| `sample-desktop` | Desktop sample app. |
| `sample-android` | Android sample app. |

## Installation

```kotlin
// settings.gradle.kts
repositories {
    google()
    mavenCentral()
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("me.vitalir:googlefonts:0.1.0")
    // optional: use Ktor as the HTTP client instead of the platform default
    implementation("me.vitalir:googlefonts-ktor:0.1.0")
}
```

## Quick start

```kotlin
import vitalir.me.googlefonts.GoogleFont
import vitalir.me.googlefonts.rememberGoogleFont
import androidx.compose.ui.text.font.FontFamily

@Composable
fun Greeting() {
    val roboto = rememberGoogleFont(GoogleFont("Roboto"), weight = FontWeight.Bold)
    Text(
        text = "Hello, Google Fonts!",
        fontFamily = roboto?.let { FontFamily(it) }, // null while loading / on failure
    )
}
```

## API

```kotlin
class GoogleFont(val name: String, val bestEffort: Boolean = true)

@Composable
fun rememberGoogleFont(
    googleFont: GoogleFont,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    onError: ((GoogleFontException) -> Unit)? = null,
): Font?

suspend fun GoogleFont.load(
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
): Font

suspend fun GoogleFont.preload(weight, style, variationSettings)

suspend fun GoogleFont.isCached(weight, style, variationSettings): Boolean

class GoogleFontException(message: String, cause: Throwable? = null) : Exception
```

- `rememberGoogleFont` — composable; returns `null` while loading or on failure (fallback text
  keeps rendering). Use `onError` to observe failures.
- `load` — suspend; returns a resolved `Font` ready for `FontFamily`. Throws `GoogleFontException`
  when the font cannot be resolved or downloaded.
- `preload` — loads and caches without returning the font.
- `isCached` — true when the font is already in memory or on disk, so `load` needs no network.

### Android initialization

`rememberGoogleFont` obtains the context from composition automatically. To call `load` /
`preload` outside composition, initialize once:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeGoogleFonts(this)
    }
}
```

## Network client

The library depends on a minimal `FontHttpClient` abstraction:

```kotlin
fun interface FontHttpClient {
    suspend fun get(url: String): ByteArray
}
```

- **Desktop (JVM)**: a zero-dependency JDK HTTP client is used by default.
- **Android**: no HTTP client is used — fonts come from the system provider.
- **iOS**: a zero-dependency `NSURLSession` client is used by default.

To inject your own client (e.g. Ktor):

```kotlin
GoogleFonts.httpClient = KtorFontHttpClient(HttpClient())
// or
GoogleFonts.useKtorClient(HttpClient())
```

## Caching & offline behavior

- Fonts are cached in memory (per process) and on disk where available
  (`~/.cache/googlefonts` on Desktop, the app caches directory on iOS).
- After the first successful load, subsequent loads — including cold starts and offline use —
  are served from the cache.
- The Google Fonts directory (`directory.xml`) is fetched only when a font is not already cached
  and is kept in memory for 7 days.
- Android additionally benefits from the OS-level provider cache.

## Platform notes & limitations

- **Android** resolves fonts through Google Play Services. `bestEffort` and variable-font
  `variationSettings` behave exactly like the AndroidX library.
- **iOS / Desktop** resolve weight/style against the Google Fonts directory and download the
  matching TTF. `variationSettings` are accepted but only applied where the platform font
  pipeline supports them (Desktop); true variable-font axis ranges are a planned enhancement.
- **Web** is not implemented yet.

## Testing

```bash
./gradlew :googlefonts:jvmTest          # common + JVM tests (parser, resolver, cache, e2e)
./gradlew :googlefonts-ktor:jvmTest     # Ktor adapter tests (MockEngine)
./gradlew :sample-android:assembleDebug # Android sample build
./gradlew :sample-desktop:run           # Desktop sample
```

## License

[Apache-2.0](LICENSE). The Android implementation is derived from
[androidx `ui-text-google-fonts`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-text-google-fonts/),
also Apache-2.0. Google Fonts are licensed under the
[SIL Open Font License](https://scripts.sil.org/OFL).