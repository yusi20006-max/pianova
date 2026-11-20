import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.0.x: the Compose compiler is shipped as a standalone Gradle plugin.
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// Download Dastkhat font files (SIL OFL) into res/font if missing.
// Source: https://github.com/dastyar-io/dastkhat-font (FaNum, square dots).
// ---------------------------------------------------------------------------
val dastkhatFonts = mapOf(
    "dastkhat_regular.ttf" to
        "https://raw.githubusercontent.com/dastyar-io/dastkhat-font/main/fonts/FaNum/DastkhatFaNum/TTF/DastkhatFaNum-Regular.ttf",
    "dastkhat_medium.ttf" to
        "https://raw.githubusercontent.com/dastyar-io/dastkhat-font/main/fonts/FaNum/DastkhatFaNum/TTF/DastkhatFaNum-Medium.ttf",
    "dastkhat_bold.ttf" to
        "https://raw.githubusercontent.com/dastyar-io/dastkhat-font/main/fonts/FaNum/DastkhatFaNum/TTF/DastkhatFaNum-Bold.ttf"
)

val fontDir = layout.projectDirectory.dir("src/main/res/font")

tasks.register("downloadDastkhatFonts") {
    group = "fonts"
    description = "Download Dastkhat Regular/Medium/Bold TTF into res/font"
    outputs.dir(fontDir)
    doLast {
        fontDir.asFile.mkdirs()
        dastkhatFonts.forEach { (name, url) ->
            val dest = fontDir.file(name).asFile
            if (!dest.exists() || dest.length() < 1000) {
                logger.lifecycle("Downloading $name …")
                URI(url).toURL().openStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                logger.lifecycle("  → ${dest.length()} bytes")
            } else {
                logger.lifecycle("Skip $name (already present, ${dest.length()} bytes)")
            }
        }
    }
}

// Ensure fonts exist before Android merges resources / compiles.
tasks.matching { it.name.startsWith("preBuild") || (it.name.startsWith("merge") && it.name.contains("Resources")) }
    .configureEach { dependsOn("downloadDastkhatFonts") }

android {
    namespace = "ir.pianova.app"
    compileSdk = 35
    // ndkVersion is intentionally NOT pinned: AGP resolves the NDK that ships with /
    // is installed into the local Android SDK. This keeps the GitHub Actions
    // ubuntu-latest image (which rotates its preinstalled NDK versions) working
    // without maintenance.

    defaultConfig {
        applicationId = "ir.pianova.app"
        minSdk = 26
        targetSdk = 35

        // CI versioning: GITHUB_RUN_NUMBER / GITHUB_SHA (fallback for local builds).
        val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        val gitSha = (System.getenv("GITHUB_SHA") ?: "local").take(7)
        val isCi = System.getenv("GITHUB_ACTIONS") == "true"

        versionCode = runNumber
        versionName = if (isCi) "0.1.0-b$runNumber" else "0.1.0-local"

        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("int", "BUILD_NUMBER", "$runNumber")

        // liboboe.so declares DT_NEEDED libc++_shared.so (its prefab abi.json
        // says "stl": "c++_shared"), so the app must build & package the shared
        // STL — see Oboe's "Getting Started" guide.
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // Jetpack Compose (Kotlin 2.0 compiler plugin handles @Composable codegen).
        compose = true
        // Consume the Oboe prefab package published by com.google.oboe:oboe.
        prefab = true
        // Generate BuildConfig with GIT_SHA and BUILD_NUMBER.
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            // No explicit version: uses the SDK's default CMake (3.22.1),
            // which is preinstalled on GitHub-hosted runners.
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM keeps all androidx.compose.* artifacts on a matching version.
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Low-latency audio (Phase 1 engine, Phase 0 smoke test).
    implementation("com.google.oboe:oboe:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
