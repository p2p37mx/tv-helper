plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.p2p37mx.tvhelper.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.p2p37mx.tvhelper.phone"
        minSdk = 28
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/tvhelper.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // finds faces in photos so the TV keeps them in view when cropping
    implementation("com.google.mlkit:face-detection:16.1.7")
}
