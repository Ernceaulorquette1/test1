val skipApp = providers.gradleProperty("skipApp").orNull == "true"

pluginManagement {
    val noApp = providers.gradleProperty("skipApp").orNull == "true"
    repositories {
        if (!noApp) google()      // Android Gradle Plugin / Jetpack: sólo si se construye :app
        mavenCentral(); gradlePluginPortal()
    }
    // Versiones por defecto de los plugins (se resuelven sólo si un módulo los solicita)
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
        id("com.android.application") version "8.7.3"
        id("com.google.dagger.hilt.android") version "2.52"
        id("com.google.devtools.ksp") version "2.0.21-1.0.28"
        id("com.google.gms.google-services") version "4.4.2"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!skipApp) google()
        mavenCentral()
    }
}
rootProject.name = "PronoScore360"
include(":core")
// :app requiere Android SDK y AGP (repositorio google()). Con -PskipApp=true sólo se construye/prueba :core.
if (!skipApp) include(":app")
