plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34 // Bajamos a 34 para evitar la restricción estricta de 16 KB

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 34 // Android 14 es más flexible con librerías nativas antiguas
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        /* Removed abiFilters to support both emulator (x86_64) and physical device (arm64) */
    }

    /* Removed packaging block to let AGP handle standard defaults */

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
    kotlinOptions {
        jvmTarget = "11"
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "VocalesRA.apk"
        }
    }
} // Llave de cierre de android

dependencies {
    // Bajamos ligeramente estas versiones para que no exijan SDK 36/37
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Tus librerías de Realidad Aumentada
    implementation(libs.arcore)
    implementation(libs.sceneview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}