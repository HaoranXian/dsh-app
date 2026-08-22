plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xianhaoran.dsh"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xianhaoran.dsh"
        minSdk = 26
        // 注意：targetSdk 34（Android 15+ 禁 targetSdk 35+ 在应用私有目录执行 ELF）
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    // v0 骨架：暂无第三方依赖，全部使用 Android 平台 API。
    // phase 2 解压 tar.xz 时再加 commons-compress + xz。
}
