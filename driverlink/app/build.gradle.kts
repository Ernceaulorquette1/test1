import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Los plugins de Firebase solo se aplican cuando existe google-services.json.
// Así el proyecto compila (flavor "demo") sin credenciales en el repositorio.
val hasGoogleServicesJson = file("google-services.json").exists()
if (hasGoogleServicesJson) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

// Claves locales (no versionadas): local.properties o variables de entorno.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localValue(key: String): String =
    localProperties.getProperty(key) ?: System.getenv(key) ?: ""

val mapsApiKey = localValue("MAPS_API_KEY")

android {
    namespace = "cl.driverlink.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.driverlink.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        manifestPlaceholders["mapsApiKey"] = mapsApiKey
        buildConfigField("boolean", "MAPS_CONFIGURED", (mapsApiKey.isNotBlank()).toString())
    }

    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            // Datos de demostración en memoria. Nunca se conecta a Firebase.
            versionNameSuffix = "-demo"
            resValue("string", "app_name", "DriverLink Demo")
            buildConfigField("boolean", "DEMO_MODE", "true")
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_name", "DriverLink")
            buildConfigField("boolean", "DEMO_MODE", "false")
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// El flavor prod necesita google-services.json; se avisa de forma explícita.
if (!hasGoogleServicesJson) {
    logger.warn(
        "DriverLink: app/google-services.json no encontrado. " +
            "El flavor 'prod' compila, pero Firebase no se inicializará. Ver FIREBASE_SETUP.md"
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.coil.compose)

    // Firebase solo en el flavor de producción: el flavor demo no lo incluye.
    "prodImplementation"(platform(libs.firebase.bom))
    "prodImplementation"(libs.firebase.auth)
    "prodImplementation"(libs.firebase.firestore)
    "prodImplementation"(libs.firebase.storage)
    "prodImplementation"(libs.firebase.messaging)
    "prodImplementation"(libs.firebase.crashlytics)
    "prodImplementation"(libs.firebase.analytics)
    "prodImplementation"(libs.firebase.functions)
    "prodImplementation"(libs.firebase.config)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
