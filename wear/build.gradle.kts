import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Same keystore as the phone app: the Wear OS Data Layer only connects a
// watch app and a phone app with the same package name AND signing key.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
fun localProp(key: String): String? = localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
val releaseStoreFile = localProp("RELEASE_STORE_FILE")?.let { File(it.replaceFirst("~", System.getProperty("user.home"))) }
val hasReleaseKeystore = releaseStoreFile?.exists() == true &&
    localProp("RELEASE_STORE_PASSWORD") != null && localProp("RELEASE_KEY_ALIAS") != null && localProp("RELEASE_KEY_PASSWORD") != null

val buildStamp: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

android {
    namespace = "com.zeroindex.intake.wear"
    compileSdk = 37

    defaultConfig {
        // Must equal the phone app's id for the Data Layer to pair them.
        applicationId = "com.zeroindex.intake"
        minSdk = 30
        targetSdk = 37
        versionCode = buildStamp.year % 100 * 1_000_000 + buildStamp.monthValue * 10_000 + buildStamp.dayOfMonth * 100 + buildStamp.hour
        versionName = "0.1.0+${buildStamp.toLocalDate()}"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = localProp("RELEASE_STORE_PASSWORD")
                keyAlias = localProp("RELEASE_KEY_ALIAS")
                keyPassword = localProp("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Matches the phone's debug build, so debug watch talks to debug phone.
            applicationIdSuffix = ".debug"
        }
        // Pairs with the phone's "preview" build (Intake Preview, com.zeroindex.intake.preview): the Data Layer only
        // connects a watch app and a phone app with the same package name and the same signing key, so the preview
        // phone app needs a preview watch app. Built from debug, so it is signed with the same debug key as the
        // phone's preview build.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            matchingFallbacks += "debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":wearprotocol"))
    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
}
