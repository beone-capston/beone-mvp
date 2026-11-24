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
            // 개발 서버 주소. 실기기는 `adb reverse`로 5173(화면)·8080(API)을 둘 다 연결한다(D-23 11항. member 같은 origin 프록시는 결정 37 O2-L, 프런트 설정 대기 N-FE-PROXY).
            val devServerOverride = providers.gradleProperty("beone.devServerUrl")
            val devServerUrl = devServerOverride.getOrElse("http://localhost:5173/")
            // 주소를 바꾼 빌드인지 테스트가 구분할 수 있게 둔다.
            buildConfigField("boolean", "DEV_SERVER_OVERRIDDEN", devServerOverride.isPresent.toString())
            val escapedUrl = devServerUrl.replace("\\", "\\\\").replace("\"", "\\\"")
            buildConfigField("String", "WEB_START_URL", "\"$escapedUrl\"")
        }
        release {
            // D-25·브리지 계약 v1(2026-11-16 사용자 결정): 서버 HTTPS 화면을 연다.
            // 탐색·브리지 신뢰 origin은 이 주소의 origin `https://beonepay.site` 하나다. www·HTTP·와일드카드는 넣지 않는다.
            buildConfigField("String", "WEB_START_URL", "\"https://beonepay.site/\"")
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

    testOptions {
        unitTests.all { test ->
            // 요청한 개발 서버 주소를 BuildConfig와 다른 경로로 테스트에 전달한다.
            providers.gradleProperty("beone.devServerUrl").orNull?.let { requested ->
                test.systemProperty("beone.test.requestedDevServerUrl", requested)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.webkit)
    testImplementation(libs.junit)
}
