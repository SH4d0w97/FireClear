import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional stable signing (CI release job sets these from repo secrets). Without them you get a debug-signed APK.
val ksPath: String? = System.getenv("FIRECLEAR_KEYSTORE")

android {
    namespace = "app.fireclear.launcher"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.fireclear.launcher"
        minSdk = 25
        targetSdk = 28   // Fire OS 7 = Android 9
        versionCode = 6
        versionName = "2.3.1"
    }
    signingConfigs {
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("FIRECLEAR_KS_PASS")
                keyAlias = System.getenv("FIRECLEAR_KEY_ALIAS")
                keyPassword = System.getenv("FIRECLEAR_KEY_PASS")
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
