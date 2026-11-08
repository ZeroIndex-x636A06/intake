// AGP 9 has built-in Kotlin support, so the org.jetbrains.kotlin.android plugin
// is neither needed nor allowed. AGP ships an older Kotlin Gradle Plugin than
// current; this buildscript block is the documented way to run a newer one.
// See developer.android.com/build/migrate-to-built-in-kotlin.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Keep both in step with [versions] kotlin / ksp in gradle/libs.versions.toml.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
