import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.0.x: the Compose compiler is shipped as a standalone Gradle plugin.
    id("org.jetbrains.kotlin.plugin.compose")
}

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
        versionCode = 1
        versionName = "0.1.0"

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

    // Low-latency audio (Phase 1 engine, Phase 0 smoke test).
    implementation("com.google.oboe:oboe:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
