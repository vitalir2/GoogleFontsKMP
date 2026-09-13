# AGENTS.md

Project-specific guidance for working on GoogleFontsKMP. Works together with the workspace-level `AGENTS.md` conventions.

## Project

GoogleFontsKMP - a Kotlin Multiplatform port of AndroidX `ui-text-google-fonts`: load Google Fonts at runtime in Compose Multiplatform for **Android**, **iOS** (arm64 + simulator), and **Desktop** (JVM). Web (JS/Wasm) is planned. **Not yet published to Maven Central** - under active development; the API may change.

## Modules & targets

| Module | Purpose |
|---|---|
| `googlefonts` | Core library. Targets: `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`. `explicitApi()`. |
| `googlefonts-ktor` | Optional Ktor-backed `FontHttpClient`. Same targets. |
| `sample-desktop`, `sample-android` | Samples - NOT published, no Dokka, no release. |

Stack: Gradle 9.5.1, Kotlin 2.4.0, Compose Multiplatform 1.11.1, AGP 9.2.0 (`com.android.kotlin.multiplatform.library`), version catalog in `gradle/libs.versions.toml`.

## Build & test

```bash
# JVM tests (common + JVM integration tests)
./gradlew :googlefonts:jvmTest
./gradlew :googlefonts-ktor:jvmTest

# Compile all targets
./gradlew :googlefonts:compileAndroidMain :googlefonts:compileKotlinIosSimulatorArm64 :googlefonts:compileKotlinIosArm64

# Samples
./gradlew :sample-desktop:run
./gradlew :sample-android:assembleDebug

# Docs site
./gradlew assembleDocsSite   # -> build/dokka/site
```

Full verification before committing:
`./gradlew :googlefonts:jvmTest :googlefonts-ktor:jvmTest :googlefonts:compileAndroidMain :googlefonts:compileKotlinIosSimulatorArm64 :googlefonts:compileKotlinIosArm64 :sample-desktop:compileKotlin :sample-android:assembleDebug assembleDocsSite`

Tests live in `googlefonts/src/commonTest` (unit) and `googlefonts/src/jvmTest` (integration with a `FakeHttpClient` + temp cache dir). In integration tests, always `FontFetcher.awaitIdle()` before resetting global state in `tearDown` - background prefetches otherwise leak into later tests. Android host tests are disabled, so `commonTest` effectively runs on JVM only - tests reading `internal` fields are safe there. `FakeHttpClient` tracks `requestedUrls`/`requestCount`; two concurrent different-weight fetches may legitimately fetch `directory.xml` twice (double-checked locking, last writer wins) - assert distinct URLs, not request counts. Fake font bodies must start with a valid font magic or `isPlausibleFontFile` rejects them - build them with the `ttf()` helper (`0x00010000` + payload).

## Architecture (commonMain, package `vitalir.me.googlefonts`)

- **Public API**: `GoogleFont` (+ nested `Provider`), `GoogleFonts` config object, `rememberGoogleFontFamily` (2 overloads), `Font(...)` factories, `FontHttpClient`, `GoogleFontException`.
- **Config**: `GoogleFonts.httpClient` / `cacheDir` / `directoryUrl` - `@Volatile` vars (settable test/config hooks). `GoogleFonts` is the only global state entry point.
- **Fetch pipeline**: `FontFetcher` - deduplicated downloads (one in-flight request per font via a `Mutex` + `CompletableDeferred` map) plus an in-memory byte cache. All load paths go through it. Downloads run in `FontFetcher`'s own scope, so a cancelled caller cannot poison concurrent waiters for the same font.
- **Error contract**: every load path throws only `GoogleFontException`. Wrap chokepoints: each default client's `get()` and `FontFetcher.download`. Android `awaitLoad` uses `resumeWithException` (not `cancel`, which would silently cancel the caller). Composables catch `Exception` defensively and rethrow `CancellationException`.
- **Offline behavior**: the directory is parse-then-cache (only non-empty parses are persisted); when a directory refresh fails, the stale on-disk copy is used; font bytes are validated by magic before and after caching (prevents truncated/captive-portal cache poisoning).
- **Fallbacks**: `fallback: FontFamily?` on the composable is returned while loading and on failure (AndroidX chain pattern); `fallback: Font?` on `load`/`toFontFamily` is returned instead of throwing. The multi-weight composable falls back per weight (failed face substituted or omitted + `onError`).
- **Caches**: `FontMemoryCache` (resolved `Font`s), `FontDiskCache` (font bytes), `FontDirectoryProvider` (directory.xml + 7-day TTL). All `Mutex`-guarded.
- **expect/actual**: `Platform.kt` (`loadFontInternal`, `defaultHttpClient`, `defaultCacheDir`, `readFileOrNull`/`writeFile`, `currentTimeMillis`, `getPlatformContext`, `ioDispatcher`) and `FontFactory.kt` (`createGoogleFont`, `defaultGoogleFontProvider`); android/jvm/ios actuals.
- **Android specifics**: `GoogleFontImpl` (async `AndroidFont` via `FontsContractCompat`), `ResolvedTypefaceFont`, GMS certs, `initializeGoogleFonts(context)`.

## Conventions

- `explicitApi()` is on - every public declaration needs explicit visibility + full KDoc.
- **KDoc**: document every public member with an example and `@see` cross-links. Dokka enforces it (`reportUndocumented` + `failOnWarning`) - the docs build FAILS on undocumented API or broken links.
- KDoc links must resolve: don't link to platform-only symbols from commonMain (use backticks); use full URLs for external links (relative `./file.md` links break Dokka).
- **Version**: single source of truth is `VERSION_NAME` in `gradle.properties`; read via `providers.gradleProperty("VERSION_NAME")`. Never hardcode a version in build files.
- `gradle.properties` is read as ISO-8859-1 - no non-ASCII characters (em dashes etc.) or they garble the published POM.
- **Dokka `includes` parser** (module READMEs become the module landing): the file must start with `# Module <name>`; NO heading may start with "Module" or "Package"; every link must be a full URL. `generateLanding` tasks (one per module) prepend the classifier and demote the first `#` to `##`.
- One concern per file; don't add unrelated code to existing files.

## AndroidX interop & K2 limits (settled - do not retry without new facts)

- `actual typealias GoogleFont = androidx...GoogleFont` is **impossible in K2**, verified against the androidx source (`compose/ui/ui-text-google-fonts/src/main/java/androidx/compose/ui/text/googlefonts/GoogleFont.kt`): (1) androidx's primary constructor has a default (`bestEffort = true`) which the expect would have to mirror; (2) defaults in expect declarations are prohibited when actualized via typealias (KT-62036, confirmed intentional by JetBrains); (3) without defaults, the 1-arg constructor fails the shape check ("number of value parameters is different" - synthetic default constructors do not participate). Revisit only if androidx ships a no-default overload or JetBrains lifts the prohibition.
- `GoogleFont.Provider` must stay our own class: androidx's `Provider` is opaque (private primary constructor, all fields `internal`). Our Android GMS path (`GoogleFontImpl.toFontRequest`) reads those fields; a typealias would make the provider config unreachable and break custom-provider support.
- Therefore: API **shape** parity (same parameter names/defaults) + trivial manual conversion (`GoogleFont(androidXFont.name, androidXFont.bestEffort)`) - no androidx compile dependency. Compose 1.11.1 also does not publish `iosX64`, so that target cannot be added.
- androidx source access: GitHub API `repos/androidx/androidx/contents/<path>` (base64) works; `raw.githubusercontent.com` 404s (LFS), cs.android.com and googlesource may 503. For KGP DSL questions (hierarchy templates etc.), the fastest source is the `kotlin-gradle-plugin`/`kotlin-gradle-plugin-api` `*-sources.jar` in `~/.gradle/caches` - Gradle plugin DSL is barely covered by regular docs.

## Publishing (Maven Central)

- Group `me.vitalir`; artifacts `googlefonts`, `googlefonts-ktor`. Full recipe in `DEVELOPMENT.md` (GPG key, portal user token, local vs CI).
- `release.yml`: `workflow_dispatch` (version input) or `v*` tag. Snapshots -> `publishToMavenCentral`; releases -> `publishAndReleaseToMavenCentral`.
- Docs deploy: `publish-docs.yml` (manual dispatch) - requires Pages source set to "GitHub Actions".

## Gotchas

- `Dispatchers.IO` is **internal on Kotlin/Native** - use `ioDispatcher()` (JVM/Android `Dispatchers.IO`; iOS `Dispatchers.Default.limitedParallelism(2)`).
- Never run blocking file/network I/O on the caller's dispatcher - file I/O must go through `ioDispatcher()`.
- Mutable shared config needs `@Volatile` (visibility across threads).
- Android target uses the AGP 9 `com.android.kotlin.multiplatform.library` plugin - no `org.jetbrains.kotlin.android`; compose deps use the DSL accessor in samples.
- JVM cache writes are atomic: unique temp file (`$path.tmp${nanoTime()}`) + `Files.move(ATOMIC_MOVE)` with non-atomic fallback. Never share a temp name between concurrent writers to the same path - the second `Files.move` fails with `NoSuchFileException`.
- kotlinx-coroutines stack-trace recovery inserts a **copy** of an exception when it crosses a coroutine boundary (e.g. `FontFetcher`'s download scope -> `deferred.await()`): `error.cause` may be the recovered copy, not the original cause. In tests, walk the chain: `generateSequence(error) { it.cause }.last()`.
- Dokka `failOnWarning` counts undocumented data-class properties individually - a public `data class` needs KDoc on every property, not just the class.
- Verify the version pipeline with `./gradlew :googlefonts:assemble -PVERSION_NAME=9.9.9` and check the artifact names in `build/libs` contain 9.9.9.