import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")

    // Must be applied AFTER the android/kotlin plugins above - it needs the
    // android {} block already configured so it knows which applicationId
    // (com.dani.jarzy) to match against app/google-services.json.
    id("com.google.gms.google-services")
}

android {
    namespace = "com.dani.jarzy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dani.jarzy"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Powers the swipeable category "cubes" on ChildDetailActivity's Categories tab.
    // recyclerview is pinned to 1.3.2 rather than the newer 1.4.0 - that version
    // requires compiling against API 35, but this project compiles against 34, and
    // 1.3.2 is still compatible with viewpager2 1.1.0.
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Firebase: a BoM (Bill of Materials) is a special dependency that doesn't
    // add any code itself - it just pins a single tested-together version set
    // for every other "com.google.firebase:..." line below it, so each
    // individual Firebase artifact doesn't need its own version number.
    // Firestore is the actual cloud database used to replace/mirror Room.
    // Recent BoM versions (32+) already fold the old "-ktx" Kotlin extension
    // artifacts into the main module, so just "firebase-firestore" (no -ktx
    // suffix) already brings in the Kotlin-friendly API.
    implementation(platform("com.google.firebase:firebase-bom:35.0.2"))
    implementation("com.google.firebase:firebase-firestore")

    // Firestore's own API hands back a Task (a callback-based result), not a Kotlin
    // suspend function - this tiny library adds a single .await() extension function
    // that turns any Task into something a suspend function can just wait for, the
    // same way every Room DAO call already could. Without this, every single
    // Firestore call in the app would need to be rewritten with nested
    // .addOnSuccessListener {} callbacks instead of the straight-line suspend code
    // already used everywhere (Kotlin, n.d.).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
}
