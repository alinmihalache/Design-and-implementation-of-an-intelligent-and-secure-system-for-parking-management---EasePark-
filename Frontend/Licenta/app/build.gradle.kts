plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.example.test"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.test"
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val lifecycleVersion = "2.7.0"
    val coroutinesVersion = "1.7.3"
    val retrofitVersion = "2.9.0"
    val okhttpVersion = "4.12.0"
    val roomVersion = "2.6.1"
    val navigationVersion = "2.7.6"
    val coreKtxVersion = "1.12.0"
    val appcompatVersion = "1.6.1"
    val materialVersion = "1.11.0"
    val constraintLayoutVersion = "2.1.4"
    val mapsVersion = "18.2.0"
    val locationVersion = "21.1.0"
    val stripeVersion = "20.36.1"
    val coilVersion = "2.5.0"
    val securityVersion = "1.1.0-alpha06"
    val jwtVersion = "2.0.2"

    // Core Android
    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("androidx.appcompat:appcompat:$appcompatVersion")
    implementation("com.google.android.material:material:$materialVersion")
    implementation("androidx.constraintlayout:constraintlayout:$constraintLayoutVersion")
    
    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:$navigationVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navigationVersion")
    
    // Google Maps
    implementation("com.google.android.gms:play-services-maps:$mapsVersion")
    implementation("com.google.android.gms:play-services-location:$locationVersion")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutinesVersion")
    
    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    
    // Room Database
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // Retrofit for Network Calls
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    
    // JWT Token Handling
    implementation("com.auth0.android:jwtdecode:$jwtVersion")
    
    // Secure Storage for JWT
    implementation("androidx.security:security-crypto:$securityVersion")
    
    // Payment Integration - Stripe
    implementation("com.stripe:stripe-android:$stripeVersion")
    
    // Image Loading
    implementation("io.coil-kt:coil:$coilVersion")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Maps Utils
    implementation("com.google.maps.android:android-maps-utils:2.3.0")
}
