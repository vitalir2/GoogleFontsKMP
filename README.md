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

Every public declaration has a single job. Pick by what you are building:

| API | Single purpose | Use it when |
|---|---|---|
| `GoogleFont(name, bestEffort)` | Identity: *what* to load | Always — it is the entry point |
| `GoogleFont.Provider` | *Where* to resolve fonts (GMS certificates) | Custom provider configuration (Android) |
| `Font(googleFont, …)` | Hand the request to Compose's `FontFamily.Resolver` | AndroidX-style `FontFamily(Font(gf, …))` declarations |
| `rememberGoogleFontFamily(googleFont, weight…)` | One face in composition; never blocks | The default choice inside composables |
| `rememberGoogleFontFamily(googleFont, weights…)` | Several faces in composition | Multi-weight typography inside a composable |
| `GoogleFont.load(…)` | One face, imperative | Building `Typography` outside composition |
| `GoogleFont.toFontFamily(…)` | Several faces, imperative | Multi-weight themes outside composition |
| `GoogleFont.warmUp(…)` | Warm the cache at startup; never suspends | `Application.onCreate` / `main()` |
| `FontHttpClient` | Swap the transport | DI, Ktor, tests |
| `GoogleFonts.{httpClient, cacheDir, directoryUrl}` | Global environment configuration | Custom client, cache location, or directory mirror |
| `initializeGoogleFonts(context)` | Android context outside composition | `load` / `toFontFamily` from non-composable code |
| `GoogleFont.Provider.isAvailableOnDevice(context)` | Debug the provider | Wrong certificates / provider not found |
| `GoogleFontException` | The one error type | Every failure throws it or reports it through `onError` |

Signatures:

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
    weight: FontWeight = FontWeight.Normal,          // single face
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    fontProvider: GoogleFont.Provider? = null,
    fallback: FontFamily? = null,                    // returned while loading and on failure
    onError: ((GoogleFontException) -> Unit)? = null,
): FontFamily?

@Composable
fun rememberGoogleFontFamily(
    googleFont: GoogleFont,
    weights: Collection<FontWeight>,                 // multi-face overload
    style: FontStyle = FontStyle.Normal,
    fontProvider: GoogleFont.Provider? = null,
    fallback: Font? = null,                          // substituted per failed weight
    onError: ((GoogleFontException) -> Unit)? = null,
): FontFamily?

suspend fun GoogleFont.load(
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
    fontProvider: GoogleFont.Provider? = null,
    fallback: Font? = null,                          // returned instead of throwing on failure
): Font

suspend fun GoogleFont.toFontFamily(
    vararg weights: FontWeight,
    style: FontStyle = FontStyle.Normal,
    fontProvider: GoogleFont.Provider? = null,
    fallback: Font? = null,
): FontFamily

fun GoogleFont.warmUp(weight, style, variationSettings, fontProvider)  // single weight
fun GoogleFont.warmUp(vararg weights, style, fontProvider)             // parallel prefetch

// Environment configuration
object GoogleFonts {
    var httpClient: FontHttpClient?   // null → platform default
    var cacheDir: String?             // null → platform default
    var directoryUrl: String?         // null → fonts.gstatic.com/s/a/directory.xml
}

// Android only
fun GoogleFont.Provider.isAvailableOnDevice(context: Context): Boolean

class GoogleFontException(message: String, cause: Throwable? = null) : Exception
```

Guidance:

- `rememberGoogleFontFamily` is the safe default — it never blocks, renders the system font (or
  `fallback`) while loading, and reflows when ready. The `Font(...)` factory is the lower-level
  descriptor handed to Compose's resolver.
- For placeholder/error UI, compose the knobs: `fallback` renders while loading and on failure,
  and `onError` captures the failure. Errors are plain state — track them yourself:
  ```kotlin
  var error by remember { mutableStateOf<GoogleFontException?>(null) }
  val family = rememberGoogleFontFamily(GoogleFont("Roboto"), onError = { error = it })
  when {
      error != null -> Text("Font failed: ${error.message}")
      family != null -> Text("Hello!", fontFamily = family)
      else -> Text("Loading…")
  }
  ```
- Don't reach for `load`/`toFontFamily` inside composition — they suspend; use the composables.
- Don't reach for `FontHttpClient` unless you need a non-default transport; the platform default
  already handles timeouts, gzip, and error wrapping.

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
```

## Caching & offline behavior

- Fonts are cached in memory (per process) and on disk where available
  (`~/.cache/googlefonts` on Desktop, the app caches directory on iOS).
- After the first successful load, subsequent loads — including cold starts and offline use —
  are served from the cache.
- The Google Fonts directory (`directory.xml`) is fetched only when a font is not already cached
  and is kept in memory for 7 days. Point `GoogleFonts.directoryUrl` at a self-hosted mirror to
  own that traffic.
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