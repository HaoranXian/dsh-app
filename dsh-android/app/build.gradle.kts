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
        // 注意：targetSdk 必须 28——Android 对 targetSdk>=29 的应用数据目录 noexec，
        // 会导致 node 二进制 exec 报 EACCES（woaiys3 真机实测，本机 iQOO 15 复现）。
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        // 大 asset 保持原样，避免 AssetManager 压缩流在真机上被截断
        noCompress += listOf("snapshot.tar.xz", "manifest.json")
    }

    lint {
        // 个人侧载，不上架 Play；targetSdk 28 是 node 可执行的必要条件
        checkReleaseBuilds = false
        abortOnError = false
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
    // 解压快照 snapshot.tar.xz（M2）
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12")
}
