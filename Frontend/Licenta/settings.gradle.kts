pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("com.android.application") version "8.3.0" apply false
        id("org.jetbrains.kotlin.android") version "1.9.22" apply false
        id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
        id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
        id("com.google.gms.google-services") version "4.4.0" apply false
        id("androidx.navigation.safeargs") version "2.7.6" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "test"
include(":app")
