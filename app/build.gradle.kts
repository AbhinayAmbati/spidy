import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.cyclonedx)
}

// Release identity. release.yml derives these from the git tag and passes -Pspidey.versionName
// and -Pspidey.versionCode; a local or manual build falls back to the defaults below.
val spideyVersionName: String =
    providers.gradleProperty("spidey.versionName").getOrElse("0.1.23")
val spideyVersionCode: Int =
    providers.gradleProperty("spidey.versionCode").map(String::toInt).getOrElse(24)

android {
    namespace = "com.example.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.agent"
        minSdk = 26
        targetSdk = 34
        versionCode = spideyVersionCode
        versionName = spideyVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // On-device inference (the Spidey model on the vendor engine). The native bridge is only built once the engine has
    // been fetched with tools/fetch_engine.py; without it the app builds as before and
    // the on-device provider reports itself unavailable.
    if (file("src/main/cpp/engine/libneedle.a").exists()) {
        defaultConfig {
            ndk { abiFilters += "arm64-v8a" }
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DANDROID_STL=c++_shared",
                        // NDK r27+: build for devices with 16 KB memory pages. Older NDKs ignore it.
                        "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    )
                }
            }
        }
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    }

    // Release signing is read from keystore.properties, which is git-ignored and
    // never committed. With no such file, release builds fall back to the debug key
    // so the project always builds out of the box. Never hard-code a secret here.
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val hasReleaseKeystore = keystorePropertiesFile.exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val props = Properties().apply {
                    keystorePropertiesFile.inputStream().use { load(it) }
                }
                // CI writes an absolute path; a local keystore.properties can use a
                // path relative to the repository root. Both resolve correctly here.
                val storePath = props.getProperty("storeFile")
                storeFile = if (File(storePath).isAbsolute) {
                    File(storePath)
                } else {
                    rootProject.file(storePath)
                }
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // Defaults to PKCS#12 (the format CI generates); override in
                // keystore.properties with storeType=JKS if you use a JKS keystore.
                storeType = props.getProperty("storeType") ?: "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            // Wires JaCoCo into the debug unit tests. ./gradlew createDebugUnitTestCoverageReport
            // writes app/build/reports/coverage/test/debug/ (HTML and report.xml).
            enableUnitTestCoverage = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Native libraries are stored uncompressed and page-aligned in the APK and mapped straight
        // from it (extractNativeLibs=false): smaller install, nothing extra written to disk, and
        // the 16 KB alignment is preserved. minSdk 26 supports this. Re-run
        // tools/check_native_alignment.py before ever switching to legacy packaging.
        jniLibs {
            useLegacyPackaging = false
        }
    }
    // Lint fails the build, but only against a triaged baseline: with no app/lint-baseline.xml
    // every existing finding would fail it, so CI keeps lint non-blocking until the baseline is
    // committed. Create it with the "update_lint_baseline" input of the Android CI workflow
    // (docs/RELEASING.md, "Lint baseline"); from then on only new issues fail the build.
    lint {
        abortOnError = true
        val lintBaseline = file("lint-baseline.xml")
        if (lintBaseline.exists()) {
            baseline = lintBaseline
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// The release artefact is named spidy.apk rather than the default app-release.apk,
// so what gets attached to a GitHub Release matches the app's name.
androidComponents {
    onVariants { variant ->
        if (variant.buildType == "release") {
            variant.outputs.forEach { output ->
                // outputFileName is exposed on the implementation type only, not on
                // the public VariantOutput interface. The safe cast means a future
                // AGP that drops it degrades to the default name instead of failing
                // the build; the release workflow also normalises the name.
                (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                    ?.outputFileName
                    ?.set("spidy.apk")
            }
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security (encrypted credential storage)
    implementation(libs.androidx.security.crypto)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Software bill of materials: ./gradlew :app:cyclonedxDirectBom writes
// app/build/reports/cyclonedx-direct/bom.json. Only the release runtime classpath is listed
// (what actually ships), so the vulnerability scan of it is about what users run, not test code.
tasks.cyclonedxDirectBom {
    projectType = org.cyclonedx.model.Component.Type.APPLICATION
    includeConfigs = listOf("releaseRuntimeClasspath")
}
