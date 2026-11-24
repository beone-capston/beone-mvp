plugins {
    alias(libs.plugins.android.application)
}

// release 서명 값은 저장소 밖에서 환경 변수로만 받는다(결정 38-9). 값은 로그·BuildConfig에 넣지 않는다.
// 키 생성·보관·백업 방식은 N8 결정·승인 대기다.
val releaseSigningEnvNames = listOf(
    "BEONE_RELEASE_STORE_FILE",
    "BEONE_RELEASE_STORE_PASSWORD",
    "BEONE_RELEASE_KEY_ALIAS",
    "BEONE_RELEASE_KEY_PASSWORD",
)
// 빈 문자열은 값이 없는 것으로 본다.
val releaseSigningEnv = releaseSigningEnvNames.associateWith { name ->
    providers.environmentVariable(name).filter { it.isNotBlank() }
}
val releaseSigningReady = releaseSigningEnv.values.all { it.isPresent }
// 배포용 빌드에서만 켠다. 켜져 있는데 값이 없으면 구성 단계에서 실패한다.
val requireReleaseSigning = providers.gradleProperty("beone.release.requireSigning")
    .map { it.toBoolean() }
    .getOrElse(false)

if (requireReleaseSigning && !releaseSigningReady) {
    val missing = releaseSigningEnv.filterValues { !it.isPresent }.keys
    throw GradleException(
        "beone.release.requireSigning=true 이지만 release 서명 환경 변수가 없습니다: " +
            missing.joinToString(", "),
    )
}

android {
    namespace = "com.beone.app"
    compileSdk = 36

    signingConfigs {
        // 4개 값이 모두 있을 때만 만든다. 없으면 release는 지금처럼 서명 없이 빌드된다.
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseSigningEnv.getValue("BEONE_RELEASE_STORE_FILE").get())
                storePassword = releaseSigningEnv.getValue("BEONE_RELEASE_STORE_PASSWORD").get()
                keyAlias = releaseSigningEnv.getValue("BEONE_RELEASE_KEY_ALIAS").get()
                keyPassword = releaseSigningEnv.getValue("BEONE_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

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
            // 서명 환경 변수가 없으면 null로 두어 app-release-unsigned.apk를 만든다.
            signingConfig = if (releaseSigningReady) signingConfigs.getByName("release") else null
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
