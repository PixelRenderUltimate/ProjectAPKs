plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Native probe (Vulkan) bisa dimatikan:  ./gradlew assembleDebug -Ppixelrender.native=false
val nativeEnabled = (providers.gradleProperty("pixelrender.native").orNull ?: "true").toBoolean()

android {
    namespace = "com.pixelrender.app"
    compileSdk = 35

    // Hanya dipakai kalau native probe diaktifkan.
    if (nativeEnabled) {
        ndkVersion = "27.0.12077973"
    }

    defaultConfig {
        applicationId = "com.pixelrender.app"
        // Vulkan (libvulkan.so) hanya ada sejak API 24. Di bawah itu Vulkan
        // dipastikan tidak ada, jadi 24 adalah batas kompatibilitas terluas
        // yang masih masuk akal untuk aplikasi ini.
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0-phase7"

        if (nativeEnabled) {
            ndk {
                // x86/x86_64: tanpa ini APK tidak bisa dipasang di emulator dan Chromebook.
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            }
            externalNativeBuild {
                cmake {
                    cppFlags += listOf("-std=c++17", "-fno-exceptions", "-fno-rtti")
                }
            }
        }
        buildConfigField("boolean", "NATIVE_VULKAN_PROBE", nativeEnabled.toString())
    }

    if (nativeEnabled) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
        // PHASE 2: Shizuku user service memakai AIDL.
        // AGP 8 mematikan AIDL secara default, jadi harus dinyalakan manual.
        aidl = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Shizuku: dipakai HANYA sebagai privileged backend opsional.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // PHASE 7: unit test JVM murni, tanpa Android SDK dan tanpa perangkat.
    testImplementation(libs.junit)
}
