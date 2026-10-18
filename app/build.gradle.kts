import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties()
rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(signingProperties::load)
fun signingValue(environmentName: String, propertyName: String): String? =
    System.getenv(environmentName)?.takeIf { it.isNotBlank() }
        ?: signingProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val signingStoreFile = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
val signingStorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val signingKeyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
val signingKeyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
val persistentSigningValues = listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword)
check(persistentSigningValues.all { it == null } || persistentSigningValues.all { !it.isNullOrBlank() }) {
    "Persistent APK signing is only partially configured. Set all keystore values or none."
}
val debugSigningStoreFile = signingValue("ANDROID_DEBUG_KEYSTORE_PATH", "debugStoreFile")
val debugSigningStorePassword = signingValue("ANDROID_DEBUG_KEYSTORE_PASSWORD", "debugStorePassword")
val debugSigningKeyAlias = signingValue("ANDROID_DEBUG_KEY_ALIAS", "debugKeyAlias")
val debugSigningKeyPassword = signingValue("ANDROID_DEBUG_KEY_PASSWORD", "debugKeyPassword")
val stableDebugSigningValues = listOf(debugSigningStoreFile, debugSigningStorePassword, debugSigningKeyAlias, debugSigningKeyPassword)
check(stableDebugSigningValues.all { it == null } || stableDebugSigningValues.all { !it.isNullOrBlank() }) {
    "Stable debug signing is only partially configured. Set all debug keystore values or none."
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
    if (stableDebugSigningValues.all { !it.isNullOrBlank() }) {
        signingConfigs.create("stableDebug") {
            storeFile = file(debugSigningStoreFile!!)
            storePassword = debugSigningStorePassword
            keyAlias = debugSigningKeyAlias
            keyPassword = debugSigningKeyPassword
        }
    }

    buildTypes {
        debug {
            if (stableDebugSigningValues.all { !it.isNullOrBlank() }) signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            manifestPlaceholders["clarityAllowBackup"] = "false"
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
