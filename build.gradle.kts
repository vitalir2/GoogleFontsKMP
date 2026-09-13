plugins {
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.dokka)
}

/**
 * Assembles the combined docs site: each module's Dokka HTML output plus a landing index.
 * Dokka 2.x generates one site per module, so this merges them under `build/dokka/site`.
 */
val assembleDocsSite by tasks.registering {
    dependsOn(
        ":googlefonts:dokkaGeneratePublicationHtml",
        ":googlefonts-ktor:dokkaGeneratePublicationHtml",
    )
    val googlefontsHtml = project(":googlefonts").layout.buildDirectory.dir("dokka/html")
    val ktorHtml = project(":googlefonts-ktor").layout.buildDirectory.dir("dokka/html")
    val outputDir = layout.buildDirectory.dir("dokka/site")
    inputs.dir(googlefontsHtml)
    inputs.dir(ktorHtml)
    outputs.dir(outputDir)
    doLast {
        val site = outputDir.get().asFile
        site.deleteRecursively()
        site.mkdirs()
        googlefontsHtml.get().asFile.copyRecursively(File(site, "googlefonts"))
        ktorHtml.get().asFile.copyRecursively(File(site, "googlefonts-ktor"))
        File(site, "index.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>GoogleFontsKMP — API reference</title>
              <style>
                body { font-family: system-ui, sans-serif; max-width: 640px; margin: 4rem auto; padding: 0 1rem; line-height: 1.6; }
                a { color: #0366d6; text-decoration: none; }
                a:hover { text-decoration: underline; }
              </style>
            </head>
            <body>
              <h1>GoogleFontsKMP</h1>
              <p>Load Google Fonts at runtime in Compose Multiplatform — Android, iOS, Desktop.</p>
              <ul>
                <li><a href="googlefonts/">googlefonts</a> — the library</li>
                <li><a href="googlefonts-ktor/">googlefonts-ktor</a> — Ktor HTTP client adapter</li>
              </ul>
            </body>
            </html>
            """.trimIndent(),
        )
    }
}