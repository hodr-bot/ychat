plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "org.ychat"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.ychat"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
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
}

dependencies {
    implementation("androidx.activity:activity:1.9.2")
}
