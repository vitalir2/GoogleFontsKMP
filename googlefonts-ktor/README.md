# googlefonts-ktor

A Ktor-backed HTTP client adapter for GoogleFontsKMP.

Coordinates: `me.vitalir:googlefonts-ktor`

## When to use it

The core `googlefonts` module uses a zero-dependency platform client by default (the JDK HTTP client on Desktop, `NSURLSession` on iOS). Use `googlefonts-ktor` when you want to:

- reuse an existing Ktor `HttpClient` (shared engine, plugins, logging);
- control the HTTP stack on a platform without a default client.

## Usage

```kotlin
import io.ktor.client.HttpClient
import vitalir.me.googlefonts.GoogleFonts
import vitalir.me.googlefonts.ktor.useKtorClient

GoogleFonts.useKtorClient(HttpClient())
```

or, equivalently:

```kotlin
GoogleFonts.httpClient = KtorFontHttpClient(HttpClient())
```

The library does not close the client — manage its lifecycle yourself.

## Installation

```kotlin
implementation("me.vitalir:googlefonts-ktor:0.1.0")
```

## Documentation

See the [root README](https://github.com/vitalir2/GoogleFontsKMP#readme) for the full guide, and the [API reference](https://vitalir2.github.io/GoogleFontsKMP/googlefonts-ktor/) for the complete API.