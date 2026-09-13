# GoogleFontsKMP

Load [Google Fonts](https://fonts.google.com) at runtime in Compose Multiplatform — Android, iOS,
Desktop (JVM) and Web (JS/Wasm, planned). A Kotlin Multiplatform port of the AndroidX
`ui-text-google-fonts` downloadable-fonts integration.

> **Status: not published yet.** GoogleFontsKMP is under active development and has not been
> released to Maven Central. The API may change at any time — research the sources and adapt the
> library to your own use case before relying on it.

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

## Project structure

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

Both artifacts are published on Maven Central.

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

For drop-in compatibility with the AndroidX `ui-text-google-fonts` API, the `Font(...)` factory is
also provided:

```kotlin
import vitalir.me.googlefonts.Font
import vitalir.me.googlefonts.GoogleFont
import androidx.compose.ui.text.font.FontFamily

val fontFamily = FontFamily(
    Font(googleFont = GoogleFont("Roboto"), weight = FontWeight.Bold, style = FontStyle.Italic)
)
```

## API

```kotlin
class GoogleFont(val name: String, val bestEffort: Boolean = true) {
    class Provider(providerAuthority: String, providerPackage: String, certificates: List<List<ByteArray>>)
    class Provider(providerAuthority: String, providerPackage: String, certificates: Int) // Android res array
}

// AndroidX-compatible Font(...) factories (non-suspend descriptors)
fun Font(googleFont: GoogleFont, fontProvider: GoogleFont.Provider,
         weight: FontWeight = Normal, style: FontStyle = Normal,
         variationSettings: FontVariation.Settings = FontVariation.Settings()): Font
fun Font(googleFont: GoogleFont,
         weight: FontWeight = Normal, style: FontStyle = Normal,
         variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style)): Font

@Composable
fun rememberGoogleFontFamily(
    googleFont: GoogleFont,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    onError: ((GoogleFontException) -> Unit)? = null,
): FontFamily?

suspend fun GoogleFont.load(
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
): Font

suspend fun GoogleFont.toFontFamily(
    vararg weights: FontWeight,
    style: FontStyle = FontStyle.Normal,
): FontFamily

fun GoogleFont.warmUp(weight, style, variationSettings) // non-suspend, fire-and-forget

// Android only
fun GoogleFont.Provider.isAvailableOnDevice(context: Context): Boolean

class GoogleFontException(message: String, cause: Throwable? = null) : Exception
```

- `Font(...)` — AndroidX-compatible non-suspend factory; returns a `Font` descriptor resolved by
  Compose's `FontFamily.Resolver`. On Android it loads asynchronously with text reflow; on
  iOS/Desktop it resolves from the cache (see Startup performance below).
- `rememberGoogleFontFamily` — composable; returns a `FontFamily` ready for `Text(fontFamily = …)`,
  or null while loading / on failure (fallback text keeps rendering). Use `onError` to observe
  failures, or pass a `fallback` font family to render while loading / on failure.
- `rememberGoogleFont` — composable; returns a `GoogleFontState` (`Loading` / `Loaded` / `Failed`)
  when you need to render a distinct placeholder or error UI.
- `load` — suspend; returns a resolved `Font`. Throws `GoogleFontException` when the font cannot
  be resolved or downloaded; with a non-null `fallback`, returns the fallback instead of throwing.
- `toFontFamily` — loads one face per weight and returns a `FontFamily`, mirroring the
  `FontFamily(Font(gf, W400), Font(gf, W700))` pattern. Accepts a `fallback` font substituted for
  weights that fail to load.
- `warmUp` — non-suspend; starts a background download so a later `load` or `Font(...)` resolution
  hits the cache. Safe to call at app startup.
- `isAvailableOnDevice` — Android; checks whether the downloadable-fonts provider is available.

### Which API should I use?

| You want to… | Use |
|---|---|
| Load a font inside a composable | `rememberGoogleFontFamily(...)` |
| Render a placeholder while loading / an error state | `rememberGoogleFont(...)` |
| Load a font from a coroutine / build a theme | `GoogleFont.load(...)` |
| Load several weights at once | `GoogleFont.toFontFamily(W400, W700, …)` |
| Preload fonts at startup | `GoogleFont.warmUp(...)` |
| Use the AndroidX API or a custom provider | `Font(googleFont, fontProvider, …)` |

`rememberGoogleFontFamily` is the safe default: it never blocks and falls back to the system font
while loading. `Font(...)` is a lower-level descriptor that Compose's resolver loads for you.

### AndroidX compatibility

The API deliberately mirrors AndroidX `ui-text-google-fonts`: `GoogleFont(name, bestEffort)`,
`GoogleFont.Provider` (certificates or a certificate resource array), and the `Font(...)` factory
keep AndroidX-compatible parameter names and defaults
(`(googleFont, fontProvider, weight = Normal, style = Normal, variationSettings = …)`), so AndroidX
call sites migrate by changing the import only. The types are not the same class — converting a
descriptor is one line, and a dedicated interop artifact may be shipped separately later:

```kotlin
val roboto = GoogleFont(androidXGoogleFont.name, androidXGoogleFont.bestEffort)
```

### Android initialization

`rememberGoogleFontFamily` obtains the context from composition automatically. To call `load` /
`toFontFamily` outside composition, initialize once:

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
  `variationSettings` behave exactly like the AndroidX library. `Font(...)` loads asynchronously
  with text reflow.
- **iOS / Desktop** resolve weight/style against the Google Fonts directory and download the
  matching TTF. `variationSettings` are applied to the loaded font on all targets.
- **Web** is not implemented yet.

## Startup performance

On iOS/Desktop, Compose's text pipeline resolves fonts synchronously during layout (no async
hook in CMP 1.11). To keep startup fast:

- **Warm the cache at startup** — call `warmUp()` (non-suspend) for the fonts your theme uses,
  e.g. in `Application.onCreate` or `main()`:
  ```kotlin
  GoogleFont("Roboto").warmUp(FontWeight.Bold)
  GoogleFont("Open Sans").warmUp()
  ```
- **Or use the composable API** — `rememberGoogleFontFamily` never blocks: text renders with the
  fallback font and reflows when the font is ready.
- **A cold `Font(...)` blocks the calling thread.** On iOS/Desktop the `Font(...)` factory has no
  async hook in CMP 1.11, so the first-ever resolution of a never-warmed font blocks composition
  for the Google Fonts directory download (several MB, once) plus the font file (~100KB), and a
  failed download throws during composition. The background prefetch it starts cannot win that
  race — `warmUp()` or the composable API is required for a non-blocking cold start. Once cached,
  `Font(...)` resolution is instant.
- The Google Fonts directory is cached on disk with a 7-day refresh TTL. When a refresh fetch
  fails (for example while offline), the stale on-disk directory is used instead of failing, so
  fonts can still be resolved offline.

## Preloading with the Compose resolver

A `FontFamily` returned by `toFontFamily` can also be preloaded through Compose's native
`FontFamily.Resolver` (reflow-aware), e.g. before starting the UI:

```kotlin
val resolver = createFontFamilyResolver()
resolver.preload(GoogleFont("Roboto").toFontFamily(FontWeight.Normal, FontWeight.Bold))
```

## Development

Building, testing, and publishing instructions live in [DEVELOPMENT.md](https://github.com/vitalir2/GoogleFontsKMP/blob/main/DEVELOPMENT.md).

## License

[Apache-2.0](https://github.com/vitalir2/GoogleFontsKMP/blob/main/LICENSE). The Android implementation is derived from
[androidx `ui-text-google-fonts`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-text-google-fonts/),
also Apache-2.0. Google Fonts are licensed under the
[SIL Open Font License](https://scripts.sil.org/OFL).