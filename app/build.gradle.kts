plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.muhan.messagesync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.muhan.messagesync"
        minSdk = 23
        targetSdk = 34
        versionCode = 4
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/release.keystore")
            storePassword = "muhan2024sync"
            keyAlias = "muhan"
            keyPassword = "muhan2024sync"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/NOTICE.md",
                "/META-INF/LICENSE.md",
                "/META-INF/NOTICE.txt",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/LICENSE"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // JavaMail for Android：SMTP 发信 / IMAP 收信（支持 IDLE）
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
}
