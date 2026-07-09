plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.taesung.widget"
    compileSdk = 36

    defaultConfig {
        // 기존 TWA 앱과 동일 패키지 — 같은 서명키로 빌드 시 '업데이트' 설치됨
        applicationId = "org.duckdns.taesung_urban.twa"
        minSdk = 24
        targetSdk = 36
        versionCode = 106
        versionName = "2.6"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_FILE")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 환경변수로 키스토어가 주어진 경우에만 release 서명 적용
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // TWA — ERP 웹을 전체화면 앱으로 (구글 공식 라이브러리)
    implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.5.0")
    // FCM 네이티브 푸시
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")
}
