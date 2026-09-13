plugins {
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

/**
 * Assembles the combined docs site: the checked-in `website/` landing plus each module's Dokka
 * HTML output. Dokka 2.x generates one site per module, so this merges them under
 * `build/dokka/site`.
 */
val assembleDocsSite by tasks.registering {
    dependsOn(
        ":googlefonts:dokkaGeneratePublicationHtml",
        ":googlefonts-ktor:dokkaGeneratePublicationHtml",
    )
    val googlefontsHtml = project(":googlefonts").layout.buildDirectory.dir("dokka/html")
    val ktorHtml = project(":googlefonts-ktor").layout.buildDirectory.dir("dokka/html")
    val website = layout.projectDirectory.dir("website")
    val outputDir = layout.buildDirectory.dir("dokka/site")
    inputs.dir(googlefontsHtml)
    inputs.dir(ktorHtml)
    inputs.dir(website)
    outputs.dir(outputDir)
    doLast {
        val site = outputDir.get().asFile
        site.deleteRecursively()
        site.mkdirs()
        website.asFile.copyRecursively(site)
        googlefontsHtml.get().asFile.copyRecursively(File(site, "googlefonts"))
        ktorHtml.get().asFile.copyRecursively(File(site, "googlefonts-ktor"))
    }
}