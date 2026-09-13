# GoogleFontsKMP — Review Report

Overall: clean architecture (deduplicated fetch pipeline, memory+disk cache, AndroidX-compatible descriptor API, good KDoc and tests for the happy path). The issues below are ordered by impact, grouped as requested: **UX first, then bugs, then other**. Every item lists the reasoning and a concrete fix.

---

## Part 1 — User experience issues

### UX-1 (critical): The error contract is broken — failures crash the app or throw undocumented exceptions

`rememberGoogleFontFamily` catches **only** `GoogleFontException` ([RememberGoogleFont.kt:62](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/RememberGoogleFont.kt#L62)), and `GoogleFont.load` documents `@throws GoogleFontException`. But the shipped HTTP clients don't uphold that contract:

- `JdkFontHttpClient` wraps only non-2xx statuses ([JdkFontHttpClient.jvm.kt:25-27](googlefonts/src/jvmMain/kotlin/vitalir/me/googlefonts/JdkFontHttpClient.jvm.kt#L25-L27)). A DNS failure, refused connection, or TLS error throws a raw `IOException` from `HttpClient.send`. `FontHttpClient`'s own KDoc demands `@throws GoogleFontException` — the default JVM client violates its own interface.
- `KtorFontHttpClient` has the same hole ([KtorFontHttpClient.kt:31-35](googlefonts-ktor/src/commonMain/kotlin/vitalir/me/googlefonts/ktor/KtorFontHttpClient.kt#L31-L35)): `client.get(url)` throws ktor/network exceptions that are never wrapped.
- Only `IosFontHttpClient` wraps correctly.

Consequence: a Desktop app that is **offline on first load crashes** — the `IOException` escapes `LaunchedEffect` and takes the app down — instead of the documented "returns null, fallback font keeps rendering, `onError` fires".

**Fix (two layers, both cheap):**
1. Wrap the whole body of both clients: `runCatching { ... }.getOrElse { throw GoogleFontException("GET $url failed", it) }`.
2. Defensively, in `rememberGoogleFontFamily` catch `Exception` (and rethrow `CancellationException`) so a misbehaving custom `FontHttpClient` can never crash a composable. Wrapping once in `FontFetcher.download` is another good chokepoint.

### UX-2 (critical): On Android, a failed font load silently cancels the caller — `onError` never fires

`GoogleFontTypefaceLoader.awaitLoad` mirrors AndroidX and calls `continuation.cancel(IllegalStateException(...))` on provider failure ([GoogleFontLoader.android.kt:149-155](googlefonts/src/androidMain/kotlin/vitalir/me/googlefonts/GoogleFontLoader.android.kt#L149-L155)). That is fine inside AndroidX, where Compose's resolver wraps `awaitLoad` in `runCatching` — but this library calls it directly from `loadFontInternal` ([Platform.android.kt:27](googlefonts/src/androidMain/kotlin/vitalir/me/googlefonts/Platform.android.kt#L27)) with no handling. The caller receives a `CancellationException`, so:

- In `rememberGoogleFontFamily`: the `catch (GoogleFontException)` misses it; the `LaunchedEffect` job is treated as cancelled — **no crash, no `onError`, font stays null forever**. The nicely formatted `reasonToString(...)` message is thrown away.
- In user code: `load()` inside `launch`/`coroutineScope` **silently cancels the user's whole scope** (e.g. the ViewModel job dies with no error).

**Fix:** in `awaitLoad`'s failure path, use `continuation.resumeWithException(GoogleFontException(...))` instead of `cancel(...)` (AndroidX parity doesn't matter here — this library owns the call site), or catch it in `loadFontInternal` and rethrow as `GoogleFontException`, distinguishing real cancellation via `currentCoroutineContext().isActive`.

### UX-3 (high): `Font(...)` on iOS/Desktop blocks the UI thread and crashes on failure

`buildLoadedFont` supplies `getData = { runBlocking { FontFetcher.fetch(...) } }` ([Platform.jvm.kt:44-51](googlefonts/src/jvmMain/kotlin/vitalir/me/googlefonts/Platform.jvm.kt#L44-L51), [Platform.ios.kt:55-61](googlefonts/src/iosMain/kotlin/vitalir/me/googlefonts/Platform.ios.kt#L55-L61)). The CMP factory `Font(identity, getData, ...)` invokes `getData` eagerly (it's deprecated precisely in favor of preloaded bytes; the library's own tests rely on `Font(...)` returning a `LoadedFont` with `.data` already materialized). So on a cold cache, creating the descriptor runs `runBlocking` for the **full multi-megabyte directory.xml download plus the font file on the calling thread** (composition/main). Two consequences:

- The `fetchAsync` "prefetch" launched one line earlier can never win the race — `runBlocking` joins the same in-flight download and waits it out. The README's "prefetches in the background … so the first layout usually hits the cache" doesn't hold.
- If the download fails, the raw exception is thrown from `getData` **inside composition → app crash**.
- The README's "blocks the first render once (download ~100KB)" underplays it: the first *ever* load also pulls the multi-MB directory.

**Fix:** there is no async hook in CMP 1.11 for this path, so: (a) make `getData` exception-safe (log + rethrow controlled, or return last-good bytes) so it can't crash composition; (b) fix the README to state plainly that a cold `Font(...)` blocks and that `warmUp()`/`rememberGoogleFontFamily` are the recommended paths; (c) plan migration off the deprecated `getData` overload. Long-term, consider making `Font(...)` on non-Android throw `UnsupportedOperationException`-style guidance or delegate to the cache-only path.

### UX-4 (medium): No distinction between "loading" and "failed" for composables

`rememberGoogleFontFamily` returns `null` for both in-flight and failed loads. Users cannot render a placeholder vs. an error state, and with `onError = null` (the default) failures are completely invisible.

**Fix:** add an opt-in result API, e.g. `rememberGoogleFont(googleFont, …): GoogleFontState` with `Loading / Loaded(FontFamily) / Failed(GoogleFontException)`, keeping the current simple overload for the fallback-Font behavior.

### UX-5 (medium): A custom `GoogleFont.Provider` cannot be used with the recommended APIs

`loadFontInternal` hardcodes `defaultGoogleFontProvider()` ([Platform.android.kt:26](googlefonts/src/androidMain/kotlin/vitalir/me/googlefonts/Platform.android.kt#L26)), and `GoogleFont` carries no provider. So `rememberGoogleFontFamily`, `load`, `toFontFamily`, and `warmUp` can only ever use the GMS provider; custom providers work solely through the `Font(...)` descriptor — contradicting the README's suggestion that `Font(...)` is the "lower-level" option while marketing provider customization.

**Fix:** add an optional `fontProvider: GoogleFont.Provider? = null` parameter to `rememberGoogleFontFamily`/`load`/`warmUp` (defaulting to the GMS provider), and include it in the memory-cache key.

### UX-6 (medium): Stale directory + offline = gratuitous failure

`readDirectoryBytes` discards the on-disk `directory.xml` once it is older than 7 days and insists on a network fetch ([FontDirectoryProvider.kt:50-60](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/FontDirectoryProvider.kt#L50-L60)). Offline after 7 days, loading any *not-yet-cached* font fails — even though the stale directory would resolve it fine (the font files themselves have no TTL). The README's offline story quietly degrades.

**Fix:** treat the TTL as a refresh hint, not a hard expiry: on fetch failure, fall back to the stale disk copy (optionally logging). 

### UX-7 (medium): No timeouts on the default HTTP clients

`JdkFontHttpClient` configures neither connect nor request timeout ([JdkFontHttpClient.jvm.kt:15-17](googlefonts/src/jvmMain/kotlin/vitalir/me/googlefonts/JdkFontHttpClient.jvm.kt#L15-L17)) — JDK defaults are infinite, so a hung connection blocks `fetch` forever, and via `getData`/`runBlocking` (UX-3) can freeze the UI thread indefinitely. NSURLSession's default 60s is better but still a 60s main-thread block worst case.

**Fix:** `connectTimeout(...)` on the client builder and `.timeout(...)` per request (e.g. 15s); on iOS use `NSURLSessionConfiguration` with an explicit `timeoutIntervalForRequest` instead of `sharedSession`.

### UX-8 (medium): First-load cost — multi-MB directory, uncompressed on JVM

The directory is fetched lazily on the first uncached font load and is several MB. The JDK client does not send `Accept-Encoding: gzip` (JDK HttpClient has no transparent decompression), so Desktop downloads it uncompressed; NSURLSession negotiates gzip automatically.

**Fix:** send `Accept-Encoding: gzip` and gunzip when `Content-Encoding: gzip` is present (small helper); document the first-load cost.

### UX-9 (minor): API paper cuts

- `rememberGoogleFontFamily` keys `remember`/`LaunchedEffect` on `googleFont.name` only ([RememberGoogleFont.kt:56-59](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/RememberGoogleFont.kt#L56-L59)) — changing `bestEffort` won't restart the load (stale font). Key on the whole request; also capture `onError` with `rememberUpdatedState` to avoid a stale closure.
- `GoogleFont` has no `equals`/`hashCode` ([GoogleFont.kt:24-30](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/GoogleFont.kt#L24-L30)) while its nested `Provider` does — value-like descriptors surprise users in sets/maps. Implement them (see also Bug-5).
- `toFontFamily(vararg weights: FontWeight)` loads weights **sequentially** ([GoogleFonts.kt:105](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/GoogleFonts.kt#L105)); use `coroutineScope { map { async { … } }.awaitAll() }`. Also: an empty `weights` silently yields an empty `FontFamily` — require at least one, and consider a `List<FontWeight>` overload.
- `warmUp`'s `variationSettings` parameter is accepted but never used ([GoogleFonts.kt:128-134](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/GoogleFonts.kt#L128-L134)) — misleading; drop it or document that the file is variation-independent.
- The Android no-context error says "use rememberGoogleFont." — the function is `rememberGoogleFontFamily` ([Platform.android.kt:24](googlefonts/src/androidMain/kotlin/vitalir/me/googlefonts/Platform.android.kt#L24)).
- `minSdk 29` is unnecessarily high: the code itself guards API 26/28/31 properly and `FontsContractCompat` works far below 29. This excludes a large device population for no functional reason — lower it (21/24) unless there's an unstated requirement.
- Top-level `Font(...)` unavoidably clashes by name with `androidx.compose.ui.text.font.Font` (inherent to the AndroidX-compatible design; just keep documenting it).

---

## Part 2 — Bugs

### Bug-1 (high): JVM disk-cache writes are non-atomic and bodies are unvalidated — permanent cache poisoning

`writeFile` on JVM writes directly to the final path ([Platform.jvm.kt:71-77](googlefonts/src/jvmMain/kotlin/vitalir/me/googlefonts/Platform.jvm.kt#L71-L77)) — iOS correctly uses `writeToFile(atomically: true)` ([Platform.ios.kt:92](googlefonts/src/iosMain/kotlin/vitalir/me/googlefonts/Platform.ios.kt#L92)). A crash/kill mid-write leaves a truncated file that is served **forever** (no TTL/validation for fonts). Additionally, nothing validates the downloaded body: a captive-portal 200-with-HTML response gets cached as a `.ttf` permanently, and a truncated `directory.xml` (same non-atomic write) parses to garbage → persistent misleading "font not found" errors.

**Fix:** on JVM write to `file.tmp` then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`; validate font bytes before caching (TTF magic `0x00010000`, `OTTO`, `ttcf`, `true`, `wOFF`); for the directory, parse-then-cache (only persist XML that parses to a non-empty directory).

### Bug-2 / Bug-3: covered above as UX-1 (unwrapped client exceptions) and UX-2 (Android `CancellationException` path) — they are the same roots, listed once there.

### Bug-4 (medium): One cancelled waiter poisons all concurrent waiters of the same font

In `FontFetcher.fetch`, the winning caller runs the download in **its own** coroutine; if it is cancelled mid-download, `catch` completes the shared deferred exceptionally with that `CancellationException` ([FontFetcher.kt:70-81](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/FontFetcher.kt#L70-L81)) — every concurrent waiter is then spuriously cancelled (e.g. one screen leaving cancels another screen's load of the same font, silently).

**Fix:** run the download in `FontFetcher`'s own scope (`scope.async`) so the deferred is independent of any caller, or on winner-cancellation remove the `inFlight` entry and let the next waiter retry.

### Bug-5 (low): Cache keys leak `bestEffort` semantics and duplicate files

`fontFileKey` uses the **requested** (name, weight, italic) ([FontCache.kt:50-53](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/FontCache.kt#L50-L53)), not the resolved entry: (a) with `bestEffort = true`, requests for W500/W550/etc. each store their own copy of the same closest file; (b) a file fetched under `bestEffort = true` (closest match) is later served to a `bestEffort = false` request that should have failed.

**Fix:** key the file cache by the resolved URL/entry (after directory resolution) rather than the request, or include `bestEffort` in the key.

### Bug-6 (medium): Directory mutex is held across the network fetch

`FontDirectoryProvider.get` does everything — disk read, network fetch, multi-MB regex parse — inside `mutex.withLock` ([FontDirectoryProvider.kt:31-40](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/FontDirectoryProvider.kt#L31-L40)). All first-time font loads serialize behind it; combined with the missing timeouts (UX-7) one stuck fetch blocks every font load in the app.

**Fix:** double-checked locking (fetch/parse outside the lock), or the same single-flight deferred pattern `FontFetcher` already uses.

### Bug-7 (medium, verify): Release pipeline has two sources of truth for the version

Both modules hardcode `version = "0.1.0"` ([googlefonts/build.gradle.kts:10](googlefonts/build.gradle.kts#L10), [googlefonts-ktor/build.gradle.kts:8](googlefonts-ktor/build.gradle.kts#L8)) while `gradle.properties` defines `VERSION_NAME=0.1.0` and `release.yml` publishes with `-PVERSION_NAME="$VERSION"` ([release.yml:44-46](.github/workflows/release.yml#L44-L46)). Nothing in the build scripts maps the property to `project.version`, so the CI override is likely a no-op — the next release would publish 0.1.0 again (or fail on Central). **Fix:** `version = providers.gradleProperty("VERSION_NAME").getOrElse("0.1.0-SNAPSHOT")` in both modules, and verify with `./gradlew publishToMavenLocal -PVERSION_NAME=9.9.9` before the next release.

### Bug-8 (low): Variation-settings cache/remember keys are order-sensitive

`fontMemoryKey` uses `variationSettings.settings.toString()` ([FontCache.kt:56-61](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/FontCache.kt#L56-L61)) and `rememberGoogleFontFamily` does the same for its keys — the same axes in a different declaration order produce different keys (cache misses / spurious reloads). Android's descriptor path already normalizes via `sortedByAxis()`; common code should too.

---

## Part 3 — Other issues

1. **Docs overstate the `Font(...)` prefetch** (see UX-3): README "Startup performance" should clearly say a cold `Font(...)` blocks the calling thread for directory + font, and that `warmUp()` or the composable API is required for a non-blocking cold start. Also the "~100KB" figure ignores the multi-MB directory on the very first load.
2. **Reliance on a deprecated CMP API** (`Font(identity, getData, ...)`): it already triggers deprecation warnings in the library build and may be removed in future CMP — plan the migration to preloaded-bytes construction (which `load()` already enables via the cache).
3. **Parser robustness** ([FontDirectory.kt:37-69](googlefonts/src/commonMain/kotlin/vitalir/me/googlefonts/FontDirectory.kt#L37-L69)): the attribute regex only accepts single-quoted values — a switch to double quotes by Google would silently yield an empty directory ("font not found" for everything). Cheap hardening: accept both quote characters. Also the parse of the multi-MB XML runs on the calling path; consider caching the parsed model on disk or parsing off the critical path.
4. **Unbounded caches**: the disk cache grows forever (code has a `TODO`); no size cap/eviction, and no way to configure the directory. At minimum expose `GoogleFonts.cacheDir` (also fixes the hardcoded `~/.cache` ignoring `XDG_CACHE_HOME`).
5. **Dependency hygiene**: `kotlinx-coroutines-core` is `implementation` while the public API is suspend-based ([googlefonts/build.gradle.kts:66](googlefonts/build.gradle.kts#L66)) — custom `FontHttpClient` implementers need coroutines types (e.g. `CancellationException`); `api` would be safer. Also consider adding `iosX64` for completeness, and note `compileSdk 37`/AGP 9.2 forces consumers to a very recent toolchain.
6. **Test gaps**: no test covers (a) a client throwing non-`GoogleFontException`, (b) the Android failure path (would have caught UX-2), (c) a cold `Font(...)` descriptor (would have surfaced UX-3's race), (d) corrupted/truncated cache files. All four are cheap to add with the existing fake-client harness.

---

## Proposed fix plan (if you approve implementation)

**P0 — stop the crashes / silent failures (small, contained diffs):**
1. Wrap all exceptions in `JdkFontHttpClient` and `KtorFontHttpClient` as `GoogleFontException`; catch `Exception` (rethrow `CancellationException`) in `rememberGoogleFontFamily`.
2. Android: `resumeWithException(GoogleFontException(...))` in `awaitLoad` failure path (or convert in `loadFontInternal`).
3. JVM: atomic cache writes (temp + `Files.move`); validate TTF magic before caching; parse-then-cache for the directory.
4. Add timeouts to both default clients.

**P1 — robustness/UX:**
5. Directory: stale-disk fallback on fetch failure; double-checked locking (fetch outside mutex).
6. `FontFetcher`: run download in its own scope so caller cancellation can't poison waiters.
7. `rememberGoogleFontFamily`: full request keys (incl. `bestEffort`), `rememberUpdatedState(onError)`; fix the "rememberGoogleFont." message typo.
8. `toFontFamily` parallel loads + non-empty validation; drop/Document `warmUp`'s unused param.
9. Release version: single source of truth via `providers.gradleProperty`.

**P2 — polish:**
10. Optional `fontProvider` parameter on the main APIs; `GoogleFont.equals/hashCode`; cache key by resolved URL; gzip on JVM; README accuracy pass; tests for the four gap scenarios; minSdk discussion.

