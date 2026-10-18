// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false

    // Google Services plugin - reads app/google-services.json at build time and
    // generates the config Firebase's SDK needs to find this specific Firebase
    // project. "apply false" here, applied for real only in app/build.gradle.kts,
    // is the same pattern already used above for the other two plugins.
    id("com.google.gms.google-services") version "4.5.0" apply false
}
