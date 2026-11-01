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
        debug {
            // 개발 서버 주소. 기기에서는 `adb reverse tcp:5173 tcp:5173`으로 연결한다.
            val devServerOverride = providers.gradleProperty("beone.devServerUrl")
            val devServerUrl = devServerOverride.getOrElse("http://localhost:5173/")
            // 주소를 바꾼 빌드인지 테스트가 구분할 수 있게 둔다.
            buildConfigField("boolean", "DEV_SERVER_OVERRIDDEN", devServerOverride.isPresent.toString())
            val escapedUrl = devServerUrl.replace("\\", "\\\\").replace("\"", "\\\"")
            buildConfigField("String", "WEB_START_URL", "\"$escapedUrl\"")
        }
        release {
            // release 로딩 방식과 신뢰 origin은 미확정이다. 정해지기 전까지 비워 두어 아무 페이지도 불러오지 않는다.
            buildConfigField("String", "WEB_START_URL", "\"\"")
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
    testImplementation(libs.junit)
}
