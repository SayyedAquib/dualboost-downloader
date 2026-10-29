import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dualboost.downloader"
    compileSdk = 35

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { stream ->
            localProperties.load(stream)
        }
    }
    val defaultSlicerUrl = localProperties.getProperty("slicer.url", "")
    val defaultSlicerSecret = localProperties.getProperty("slicer.secret", "")

    val keystoreProps = Properties()
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { stream ->
            keystoreProps.load(stream)
        }
    }

    val envKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    val propKeystorePath = keystoreProps.getProperty("RELEASE_KEYSTORE_PATH") ?: keystoreProps.getProperty("storeFile")

    val resolvedStoreFile: File
    val resolvedStorePassword: String
    val resolvedKeyAlias: String
    val resolvedKeyPassword: String

    if (!envKeystorePath.isNullOrBlank()) {
        resolvedStoreFile = file(envKeystorePath)
        resolvedStorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: ""
        resolvedKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "dualboost"
        resolvedKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
    } else if (!propKeystorePath.isNullOrBlank()) {
        resolvedStoreFile = rootProject.file(propKeystorePath)
        resolvedStorePassword = keystoreProps.getProperty("RELEASE_KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword") ?: ""
        resolvedKeyAlias = keystoreProps.getProperty("RELEASE_KEY_ALIAS") ?: keystoreProps.getProperty("keyAlias") ?: "dualboost"
        resolvedKeyPassword = keystoreProps.getProperty("RELEASE_KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword") ?: ""
    } else {
        // SEC-AUDIT H-1 & Seam 10: Strict CI Signing Gate
        val isStrictSigning = System.getenv("STRICT_SIGNING")?.toBoolean() == true ||
                System.getenv("CI")?.toBoolean() == true ||
                project.hasProperty("strictSigning")
        if (isStrictSigning) {
            throw GradleException(
                "STRICT SIGNING FAILURE: Production or CI build detected without configured release keystore! " +
                "Provide RELEASE_KEYSTORE_PATH or keystore.properties before assembling release artifacts."
            )
        }
        logger.warn(
            "WARNING [DualBoost Security]: Release build is using auto-generated local keystore 'release-local.jks'. " +
            "Suitable for local CLI development only. For production releases, set STRICT_SIGNING=true and provide official signing keys."
        )

        // Auto-generate a dedicated local release keystore for CLI builds if neither env vars nor keystore.properties are supplied.
        val autoKeystore = rootProject.file("release-local.jks")
        val defaultPass = "DualBoostReleaseKey2026!"
        val defaultAlias = "dualboost-release"
        if (!autoKeystore.exists()) {
            val keytoolCmd = arrayOf(
                "keytool", "-genkeypair", "-v",
                "-keystore", autoKeystore.absolutePath,
                "-storepass", defaultPass,
                "-alias", defaultAlias,
                "-keypass", defaultPass,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-dname", "CN=DualBoost Release, OU=Release, O=DualBoost, L=Android, S=Release, C=US"
            )
            ProcessBuilder(*keytoolCmd).redirectErrorStream(true).start().waitFor()
        }
        resolvedStoreFile = autoKeystore
        resolvedStorePassword = defaultPass
        resolvedKeyAlias = defaultAlias
        resolvedKeyPassword = defaultPass
    }

    signingConfigs {
        create("release") {
            storeFile = resolvedStoreFile
            storePassword = resolvedStorePassword
            keyAlias = resolvedKeyAlias
            keyPassword = resolvedKeyPassword
        }
    }

    defaultConfig {
        applicationId = "com.dualboost.downloader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEFAULT_SLICER_URL", "\"$defaultSlicerUrl\"")
        buildConfigField("String", "DEFAULT_SLICER_SECRET", "\"$defaultSlicerSecret\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            // SEC-AUDIT H-1: Release builds are always signed with a dedicated release keystore
            signingConfig = signingConfigs.getByName("release")

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
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        checkReleaseBuilds = true
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

