import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.agent"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "0.1.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
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
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Security (encrypted credential storage)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
