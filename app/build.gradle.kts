import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.fireclear.launcher"
    compileSdk = 35

    val ksPath = System.getenv("FIRECLEAR_KEYSTORE")

    defaultConfig {
        applicationId = "app.fireclear.launcher"
        minSdk = 25
        targetSdk = 28   // Fire OS 7 = Android 9
        versionCode = 12
        versionName = "4.0.0"
    }
    signingConfigs {
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("FIRECLEAR_KS_PASS")
                keyAlias = System.getenv("FIRECLEAR_KEY_ALIAS")
                val kp = System.getenv("FIRECLEAR_KEY_PASS")
                keyPassword = if (!kp.isNullOrBlank()) kp else System.getenv("FIRECLEAR_KS_PASS")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (ksPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    lint { abortOnError = false; warningsAsErrors = false }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
// No dependencies on purpose. scripts/audit.sh fails the build if one is added.
