plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.cameramanager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cameramanager"
        minSdk = 24
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Concurrent library for asynchronous coroutines
    implementation ("androidx.concurrent:concurrent-futures-ktx:1.1.0")

    // Navigation library
    val nav_version = "2.3.5"
    implementation ("androidx.navigation:navigation-fragment-ktx:$nav_version")
    implementation ("androidx.navigation:navigation-ui-ktx:$nav_version")

    // CameraX core library
    val camerax_version = "1.1.0-beta01"
    implementation ("androidx.camera:camera-core:$camerax_version")

    // CameraX Camera2 extensions
    implementation ("androidx.camera:camera-camera2:$camerax_version")

    // CameraX Lifecycle library
    implementation ("androidx.camera:camera-lifecycle:$camerax_version")

    // CameraX View class
    implementation ("androidx.camera:camera-view:$camerax_version")

    //WindowManager
    implementation ("androidx.window:window:1.0.0-alpha09")

    // Glide
    implementation ("com.github.bumptech.glide:glide:4.12.0")
    kapt ("com.github.bumptech.glide:compiler:4.11.0")
}