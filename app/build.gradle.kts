import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val dastkhatFonts = mapOf(
    "dastkhat_regular.ttf" to "https://raw.githubusercontent.com/dastyar-io/dastkhat-font/main/fonts/FaNum/DastkhatFaNum/TTF/DastkhatFaNum-Regular.ttf",
    "dastkhat_medium.ttf" to "https://raw.githubusercontent.com/dastyar-io/dastkhat-font/main/fonts/FaNum/DastkhatFaNum/TTF/DastkhatFaNum-Medium.ttf",
    "dastkhat_bold.ttf" to "https://raw.githubusercontent.com/dastyar-io/dastkhat-font/main/fonts/FaNum/DastkhatFaNum/TTF/DastkhatFaNum-Bold.ttf"
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
                URI(url).toURL().openStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
tasks.matching { it.name.startsWith("preBuild") || (it.name.startsWith("merge") && it.name.contains("Resources")) }
    .configureEach { dependsOn("downloadDastkhatFonts") }

val audioDependencies = listOf(
    arrayOf("src/main/cpp/tsf.h", "https://raw.githubusercontent.com/schellingb/TinySoundFont/a3621a19c6504885cd9583e6e26c35bc954fc8ec/tsf.h", "92719", "a81f25d5ca2e210720d646dec2dbfaeb119acb09"),
    arrayOf("src/main/assets/soundfonts/pianova.sf2", "https://raw.githubusercontent.com/bratpeki/soundfonts/e1dcd9da45cc7454b4e3bfae613f4020d6a0b4f7/SF2/GM/ChaosBank.sf2", "12038662", "e21fc1948628f6a208804cdd893975b593b287b4")
)
fun gitBlobSha1(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
    digest.update(bytes)
    return digest.digest().joinToString("") { "%02x".format(it) }
}
tasks.register("downloadAudioDependencies") {
    group = "audio"
    description = "Fetch pinned TinySoundFont and CC0 piano SoundFont dependencies"
    outputs.files(audioDependencies.map { layout.projectDirectory.file(it[0]) })
    doLast {
        audioDependencies.forEach { dependency ->
            val (relativePath, url, expectedSize, expectedSha) = dependency
            val destination = layout.projectDirectory.file(relativePath).asFile
            destination.parentFile.mkdirs()
            val existing = if (destination.isFile) destination.readBytes() else null
            val valid = existing != null && existing.size.toString() == expectedSize && gitBlobSha1(existing) == expectedSha
            if (!valid) {
                val bytes = URI(url).toURL().openStream().use { it.readBytes() }
                check(bytes.size.toString() == expectedSize) { "Unexpected size for $relativePath: ${bytes.size}" }
                check(gitBlobSha1(bytes) == expectedSha) { "Git blob SHA-1 verification failed for $relativePath" }
                destination.writeBytes(bytes)
            }
        }
    }
}
tasks.matching {
    it.name.startsWith("preBuild") || it.name.startsWith("configureCMake") ||
        (it.name.startsWith("merge") && it.name.contains("Assets"))
}.configureEach { dependsOn("downloadAudioDependencies") }

android {
    namespace = "ir.pianova.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "ir.pianova.app"
        minSdk = 26
        targetSdk = 35
        val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        val gitSha = (System.getenv("GITHUB_SHA") ?: "local").take(7)
        val isCi = System.getenv("GITHUB_ACTIONS") == "true"
        versionCode = runNumber
        versionName = if (isCi) "0.1.0-b$runNumber" else "0.1.0-local"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("int", "BUILD_NUMBER", "$runNumber")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild { cmake { arguments("-DANDROID_STL=c++_shared") } }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        prefab = true
        buildConfig = true
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("com.google.oboe:oboe:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
