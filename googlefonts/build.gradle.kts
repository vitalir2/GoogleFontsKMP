plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
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
    moduleName.set("googlefonts")
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
        namespace = "vitalir.me.googlefonts"
        compileSdk = 37
        minSdk = 29
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}