plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.maven.publish)
}

version = "0.1.0"

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

/** Generates the Dokka landing page from the README (Dokka requires a `# Module` classifier). */
val generateLanding by tasks.registering {
    val readme = rootProject.file("README.md")
    val output = layout.buildDirectory.file("generated/dokka-landing.md")
    inputs.file(readme)
    outputs.file(output)
    doLast {
        val content = readme.readText().replaceFirst("# GoogleFontsKMP", "## GoogleFontsKMP")
        output.get().asFile.writeText("# Module ${project.name}\n\n$content")
    }
}

dokka {
    moduleName.set("googlefonts-ktor")
    dokkaSourceSets.configureEach {
        includes.from(generateLanding)
        reportUndocumented.set(true)
        skipEmptyPackages.set(true)
        sourceLink {
            localDirectory.set(layout.projectDirectory.dir("src"))
            remoteUrl("https://github.com/vitalir2/GoogleFontsKMP/blob/main")
            remoteLineSuffix.set("#L")
        }
    }
    dokkaPublications.html {
        failOnWarning.set(true)
        suppressObviousFunctions.set(true)
    }
    pluginsConfiguration.html {
        footerMessage.set("Apache-2.0 · GoogleFontsKMP")
    }
}

kotlin {
    android {
        namespace = "vitalir.me.googlefonts.ktor"
        compileSdk = 37
        minSdk = 29
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":googlefonts"))
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}