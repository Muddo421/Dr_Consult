plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.drconsult"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.drconsult"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation("io.agora.rtc:full-sdk:4.2.2")
    // --- FIX: This is the correct line for Storage ---
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation ("com.google.firebase:firebase-auth:22.3.0")
    implementation("com.google.firebase:firebase-storage:21.0.1")

    // --- Existing Dependencies (Standard Android) ---
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // --- Firebase (Auth & Firestore) ---
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database) // You might not need this if using Firestore
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

}