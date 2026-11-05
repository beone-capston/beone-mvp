plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.beone.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.beone.app"
        // API 30+: BiometricPrompt supports BIOMETRIC_STRONG | DEVICE_CREDENTIAL (PRD FR-06).
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    testImplementation(libs.junit)
}
