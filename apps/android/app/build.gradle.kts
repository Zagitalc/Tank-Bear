import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "uk.tankbear.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.tankbear.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    val betaSigningFile = rootProject.file("../../.local/beta-signing.properties")
    if (betaSigningFile.exists()) {
        val betaSecrets = Properties().apply { betaSigningFile.inputStream().use { load(it) } }
        signingConfigs.create("friendBeta") {
            storeFile = rootProject.file("../../.local/tankbear-beta.jks")
            storePassword = betaSecrets.getProperty("password")
            keyAlias = "tankbear-beta"
            keyPassword = betaSecrets.getProperty("password")
        }
    }
    buildTypes {
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.findByName("friendBeta")
            resValue("string", "app_name", "Tank Bear Beta")
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    sourceSets { getByName("test").resources.srcDir("../../../contracts/fixtures") }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.maplibre.android)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.org.json) // Android's org.json is stubbed in JVM unit tests.
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    // Older Espresso uses InputManager reflection removed on newer Android.
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
