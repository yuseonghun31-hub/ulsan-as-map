plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.ulsan.asmap"
    compileSdk = 34

    defaultConfig {
        applicationId = "kr.ulsan.asmap"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // 개인용이라 난독화 없이. 서명은 GitHub에서 빌드할 때 디버그 키로 (설치·실행에는 문제없음)
        getByName("debug") { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// 외부 라이브러리 없이 안드로이드 기본 기능만 씀 (빌드가 빠르고 고장 날 데가 적음)
