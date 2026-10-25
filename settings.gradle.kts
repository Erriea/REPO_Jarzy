pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // MPAndroidChart (the library used for the spend-per-category graph in
        // ExpenseHistoryActivity) is published on JitPack rather than Maven Central,
        // so JitPack has to be added as its own repository here before
        // "com.github.PhilJay:MPAndroidChart" can be resolved as a dependency below
        // in app/build.gradle.kts (JitPack, n.d.).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Jarzy"
include(":app")
