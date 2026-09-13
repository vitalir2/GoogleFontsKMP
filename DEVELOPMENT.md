# Development

Developer guide for GoogleFontsKMP: building, testing, generating docs, and publishing to Maven Central.

## Prerequisites

- JDK 21
- Android SDK (for Android targets)
- Publishing only: a [Central Portal](https://central.sonatype.com) account with the `me.vitalir` namespace registered, a GPG key pair (public key distributed to a keyserver), and a portal user token.

## Build & test

```bash
# JVM tests (common + JVM)
./gradlew :googlefonts:jvmTest
./gradlew :googlefonts-ktor:jvmTest

# Compile all targets
./gradlew :googlefonts:compileAndroidMain \
  :googlefonts:compileKotlinIosSimulatorArm64 \
  :googlefonts:compileKotlinIosArm64

# Samples
./gradlew :sample-desktop:run
./gradlew :sample-android:assembleDebug
```

## Documentation

```bash
./gradlew assembleDocsSite   # → build/dokka/site
```

The docs site is published to GitHub Pages by the `publish-docs.yml` workflow (manual dispatch; requires the Pages source to be set to "GitHub Actions" in the repo settings).

## Publishing to Maven Central

Artifacts: `me.vitalir:googlefonts` and `me.vitalir:googlefonts-ktor`.

### Prerequisites

- Central Portal account with the `me.vitalir` namespace registered.
- A GPG key pair; the public key must be distributed to a keyserver.
- A portal user token (central.sonatype.com → User Tokens).

### Local publish

Credentials are read from Gradle properties, so you can provide them via environment variables. The GPG passphrase is never stored anywhere — you type it at the terminal:

```bash
# Portal user token
export ORG_GRADLE_PROJECT_mavenCentralUsername='<token-username>'
export ORG_GRADLE_PROJECT_mavenCentralPassword='<token-password>'

# Signing key (armored private key)
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --export-secret-keys --armor <KEY_ID>)"

# GPG passphrase — typed, not stored
printf "GPG passphrase: "
read -rs ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword

# Snapshot (goes to the Central snapshot repository)
./gradlew publishToMavenCentral -PVERSION_NAME=0.1.0-SNAPSHOT

# Release (uploads, validates, and auto-releases)
./gradlew publishAndReleaseToMavenCentral -PVERSION_NAME=0.1.0
```

### CI publish

The `release.yml` workflow publishes from GitHub Actions. It needs these repo secrets:

- `ORG_GRADLE_PROJECT_mavenCentralUsername`
- `ORG_GRADLE_PROJECT_mavenCentralPassword`
- `ORG_GRADLE_PROJECT_signingInMemoryKey`
- `ORG_GRADLE_PROJECT_signingInMemoryKeyId`
- `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`

Triggers:

- `workflow_dispatch` with a `version` input (e.g. `0.1.0-SNAPSHOT`).
- `push` of a `v*` tag (e.g. `v0.1.0` → publishes `0.1.0`).

## Release process

1. Publish a snapshot and verify it in the Central snapshot repository.
2. Create a GitHub Release `v0.1.0` with release notes → the tag triggers the CI publish.
3. Verify on Maven Central (10–30 min) and on klibs.io.

Release automation (version bump + changelog via Release Please) is planned for a later iteration.