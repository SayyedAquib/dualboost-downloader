plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dualboost.downloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dualboost.downloader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            // SEC-AUDIT C-2: Use release keystore if environment variables are set,
            // else fall back to debug for local development builds only
            val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            signingConfig = if (!releaseKeystorePath.isNullOrBlank()) {
                signingConfigs.create("release") {
                    storeFile = file(releaseKeystorePath)
                    storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: ""
                    keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
                }
            } else {
                // WARNING: Debug signing for local development only. Set env vars for production builds.
                signingConfigs.getByName("debug")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Networking & Concurrency
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    // SEC-AUDIT C-1: Encrypted credential storage via Android Keystore
    implementation(libs.androidx.security.crypto)

    // Unit & Integration Testing
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    // Android Instrumented & Jetpack Compose UI Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

configurations.all {
    resolutionStrategy {
        force("androidx.test.espresso:espresso-core:3.7.0")
        force("androidx.test:runner:1.7.0")
        force("androidx.test.ext:junit:1.3.0")
    }
}

