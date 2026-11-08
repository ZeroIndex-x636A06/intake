import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------
// Signing. SPEC §15.3: read from local.properties, which stays untracked, and
// fail a release build with a clear message when the keystore is missing.
// ---------------------------------------------------------------------------
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

fun localProp(key: String): String? = localProperties.getProperty(key)?.takeIf { it.isNotBlank() }

val releaseStoreFile = localProp("RELEASE_STORE_FILE")
    ?.let { File(it.replaceFirst("~", System.getProperty("user.home"))) }
val releaseStorePassword = localProp("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = localProp("RELEASE_KEY_ALIAS")
val releaseKeyPassword = localProp("RELEASE_KEY_PASSWORD")

val hasReleaseKeystore = releaseStoreFile?.exists() == true &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

val wantsRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

if (wantsRelease && !hasReleaseKeystore) {
    throw GradleException(
        """
        |
        |Release build requires a signing keystore, and none was found.
        |
        |1. Create one (once):
        |     keytool -genkey -v -keystore ~/.android/personal-release.jks \
        |       -keyalg RSA -keysize 2048 -validity 10000 -alias mealtrain
        |
        |2. Add these four lines to local.properties (untracked, never commit it):
        |     RELEASE_STORE_FILE=/home/you/.android/personal-release.jks
        |     RELEASE_STORE_PASSWORD=...
        |     RELEASE_KEY_ALIAS=mealtrain
        |     RELEASE_KEY_PASSWORD=...
        |
        |Looked for keystore at: ${releaseStoreFile?.absolutePath ?: "(RELEASE_STORE_FILE not set)"}
        |
        |Back the keystore up somewhere that is not this laptop: signing the app
        |with a different key later forces an uninstall, which loses all data.
        """.trimMargin()
    )
}

// SPEC §15.7: versionCode derived from the build date so successive sideloads
// upgrade in place rather than needing an uninstall. yyMMddHH stays monotonic
// and well inside Int range (max 99_123_123), and the hour means two builds on
// the same day still upgrade.
val buildStamp: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
val dateVersionCode = buildStamp.year % 100 * 1_000_000 +
    buildStamp.monthValue * 10_000 +
    buildStamp.dayOfMonth * 100 +
    buildStamp.hour

android {
    namespace = "com.zeroindex.intake"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zeroindex.intake"
        minSdk = 29
        targetSdk = 37
        versionCode = dateVersionCode
        versionName = "0.1.0+${buildStamp.toLocalDate()}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            // SPEC §15.4 + §16: minify and resource shrinking on, and the
            // release APK gets built and installed from phase 1 so R8 breakage
            // in serialization / Room / Retrofit surfaces early.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Version name and code are shown on the More screen (SPEC §13).
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    // SPEC §4: export the Room schema JSON and commit it.
    arg("room.schemaLocation", "${rootProject.projectDir}/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.androidx.health.connect.client)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
