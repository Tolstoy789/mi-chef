plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.chefdesobras.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chefdesobras.app"
        // 26 para el icono adaptable sin PNG de respaldo; MediaPipe GenAI pide 24 como mínimo.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Llave de depuración fija (no es secreta): así cada APK que compila GitHub Actions
    // se instala encima del anterior sin desinstalar y sin perder el modelo ni la despensa.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
    buildFeatures {
        compose = true
    }
    // Evita que AGP comprima un posible modelo .task dentro de assets.
    androidResources {
        noCompress += "task"
    }
}

dependencies {
    // Inferencia LLM en el dispositivo (Gemma) con MediaPipe
    implementation("com.google.mediapipe:tasks-genai:0.10.35")
    // MPImage, para pasarle al modelo la foto de los productos
    implementation("com.google.mediapipe:tasks-core:0.10.35")

    // Jetpack Compose + Material 3
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity, ViewModel y corrutinas
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
