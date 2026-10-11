import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Signing comes from a `.env` at the repo root (written by CI from repository secrets, or by
 * hand locally). Without one, debug builds use the machine's debug key and release is unsigned.
 */
val envProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file(".env")).asText.orNull
        ?.let { load(it.reader()) }
}
val hasReleaseKeystore = !envProperties.getProperty("KEYSTORE_PATH").isNullOrBlank()

android {
    namespace = "bassamalim.rune"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "bassamalim.rune"
        // 30 is where a Keystore key can demand a fresh biometric for every single use.
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(envProperties.getProperty("KEYSTORE_PATH"))
                storePassword = envProperties.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = envProperties.getProperty("KEY_ALIAS", "")
                keyPassword = envProperties.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        debug {
            // The owner's own key, so local and CI builds install over each other.
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
        release {
            optimization {
                enable = false
            }
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Settings shows the version number.
        buildConfig = true
    }
    testOptions {
        unitTests {
            // The screenshot tests render real resources (fonts, icons) under Robolectric.
            isIncludeAndroidResources = true
            all {
                // Makes ScreenshotTest write its PNGs to app/build/screenshots.
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)

    // Argon2id: Android has no built-in implementation.
    implementation(libs.bouncycastle)

    // Estimates how guessable a chosen master password is, at setup.
    implementation(libs.zxcvbn)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
