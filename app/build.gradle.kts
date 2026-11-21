import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

/**
 * Llaves de ePayco (modo pruebas). No se suben al repositorio:
 *  - En el equipo local se leen de local.properties (EPAYCO_PUBLIC_KEY y EPAYCO_PRIVATE_KEY).
 *  - En GitHub Actions se leen de los secretos del repositorio con el mismo nombre.
 */
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim()

android {
    namespace = "co.patitas.tienda"
    compileSdk = 35

    defaultConfig {
        applicationId = "co.patitas.tienda"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"

        buildConfigField("String", "EPAYCO_PUBLIC_KEY", "\"${secret("EPAYCO_PUBLIC_KEY")}\"")
        buildConfigField("String", "EPAYCO_PRIVATE_KEY", "\"${secret("EPAYCO_PRIVATE_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // ViewBinding genera una clase por cada layout XML para acceder a las vistas sin findViewById.
    // BuildConfig expone las llaves de ePayco al código.
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX y Material Components
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Ciclo de vida: corrutinas atadas a la Activity/Fragment y LiveData para observar Firestore.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // await() sobre las Task de Firebase y de Google Play Services.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Autenticación biométrica (huella) con Android Keystore
    implementation("androidx.biometric:biometric:1.1.0")

    // Firebase: Authentication y Cloud Firestore (las versiones las fija el BoM)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Google Play Services: ubicación (Fused Location Provider)
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
