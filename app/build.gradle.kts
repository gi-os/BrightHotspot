plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gios.brighthotspot"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.gios.brighthotspot"
        minSdk = 29
        targetSdk = 35
        // CI stamps versionCode from the workflow run number and appends it to the
        // major.minor below, so the release tag is <major>.<minor>.<run>. Bump this by
        // hand for anything Obtainium should treat as a new version.
        versionCode = 1
        versionName = "1.0.0"

        // Shake-to-report posts to the relay in gi-os/BrightCommon (relay/), which holds the key
        // that files the issue, so no credential ships in the APK. Blank queues reports.
        buildConfigField("String", "REPORT_RELAY_URL", "\"${System.getenv("REPORT_RELAY_URL") ?: ""}\"")
        buildConfigField("String", "REPORT_REPO", "\"gi-os/light-reports\"")

        // The LPIII is arm64 only; shipping four ABIs tripled the APK for nothing.
        ndk { abiFilters += "arm64-v8a" }
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("main") { java.srcDirs("src/main/kotlin") }
        getByName("test") { java.srcDirs("src/test/kotlin") }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate:1.2.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Shizuku: ADB-level privilege without root, so the app can call the hidden
    // tethering API that has been system-only since Android 11. api+provider is the
    // client pair; the AIDL is bundled, no separate service to ship.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Android 14 hides the reflection targets behind the hidden-API greylist; this is
    // the maintained bypass every SoftAp-via-Shizuku app uses.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    testImplementation("junit:junit:4.13.2")
}
