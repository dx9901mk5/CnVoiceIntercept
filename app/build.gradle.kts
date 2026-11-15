import java.security.KeyStore

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val fixedKeystoreFile = file("signing/cnvoiceintercept.p12")
val fixedKeystorePassword = "PhotoSyncGuard2026!"
val fixedKeyAlias: String = KeyStore.getInstance("PKCS12").apply {
    fixedKeystoreFile.inputStream().use { load(it, fixedKeystorePassword.toCharArray()) }
}.aliases().nextElement()

android {
    namespace = "com.antigravity.cnvoiceintercept"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.antigravity.cnvoiceintercept"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.2.0"
    }

    signingConfigs {
        create("fixed") {
            storeFile = fixedKeystoreFile
            storePassword = fixedKeystorePassword
            keyAlias = fixedKeyAlias
            keyPassword = fixedKeystorePassword
            storeType = "PKCS12"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
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
        aidl = true
        buildConfig = true
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

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)
}
