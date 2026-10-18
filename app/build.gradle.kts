import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties()
rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(signingProperties::load)
val signingStoreFile = System.getenv("ANDROID_KEYSTORE_PATH")
    ?: signingProperties.getProperty("storeFile")
val signingStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    ?: signingProperties.getProperty("storePassword")
val signingKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    ?: signingProperties.getProperty("keyAlias")
val signingKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
    ?: signingProperties.getProperty("keyPassword")
val persistentSigningValues = listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword)
check(persistentSigningValues.all { it == null } || persistentSigningValues.all { !it.isNullOrBlank() }) {
    "Persistent APK signing is only partially configured. Set all keystore values or none."
}

android {
    namespace = "com.example.darktodo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.darktodo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        resourceConfigurations += listOf("en")
        manifestPlaceholders["clarityAllowBackup"] = "true"
        manifestPlaceholders["clarityDebuggable"] = "false"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    if (persistentSigningValues.all { !it.isNullOrBlank() }) {
        signingConfigs.create("persistent") {
            storeFile = file(signingStoreFile!!)
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["clarityDebuggable"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            manifestPlaceholders["clarityAllowBackup"] = "false"
            manifestPlaceholders["clarityDebuggable"] = "false"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (persistentSigningValues.all { !it.isNullOrBlank() }) signingConfig = signingConfigs.getByName("persistent")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
