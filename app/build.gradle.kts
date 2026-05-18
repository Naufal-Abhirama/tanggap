plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "id.tanggap.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "id.tanggap.app"
        minSdk = 26        // LiteRT-LM butuh minimum API 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Wajib: abiFilters hanya ARM64 agar APK tidak bengkak
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true       // aktifkan R8
            isShrinkResources = true     // hapus resource (gambar, xml) yang tidak dipakai
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = false
    }

    kotlin {
        jvmToolchain(11)
    }

    buildFeatures {
        compose = true
    }

    // Wajib: jangan compress file model agar bisa di-mmap langsung
    androidResources {
        noCompress += listOf("litertlm", "bin", "task")
    }

    // Wajib untuk LiteRT-LM: izinkan duplicate .so dari dependency
    packaging {
        jniLibs {
            pickFirsts += setOf("**/*.so")
            useLegacyPackaging = true  // ← tambahkan ini
        }
    }
}

dependencies {
    // ✅ Dependency LiteRT-LM yang BENAR
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0-rc1")

    // Coroutines (untuk background inference)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("androidx.compose.animation:animation:1.7.0")

    implementation("com.google.mlkit:image-labeling:17.0.9")

    // Compose & AndroidX
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.halilibo.compose-richtext:richtext-ui-material3:0.20.0")
    implementation("com.halilibo.compose-richtext:richtext-commonmark:0.20.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}