import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 서명 키를 매번 같은 것으로 쓰기 위해 android/debug-keystore.b64 를 풀어서 그 키로 서명한다.
// (러너가 임의 키를 새로 만들면 다음 빌드에서 서명이 달라져, 폰에서 앱을 지우고 다시 깔아야 한다)
val fixedKeystore: File = layout.buildDirectory.file("signing/fixed-debug.keystore").get().asFile
if (!fixedKeystore.exists()) {
    fixedKeystore.parentFile.mkdirs()
    fixedKeystore.writeBytes(
        Base64.getMimeDecoder().decode(rootProject.file("debug-keystore.b64").readBytes())
    )
}

android {
    namespace = "kr.ulsan.asmap"
    compileSdk = 34

    defaultConfig {
        applicationId = "kr.ulsan.asmap"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = fixedKeystore
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // 개인용이라 난독화 없이. 서명은 위 고정 키로 (설치·실행에는 문제없음)
        getByName("debug") { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// 외부 라이브러리 없이 안드로이드 기본 기능만 씀 (빌드가 빠르고 고장 날 데가 적음)
