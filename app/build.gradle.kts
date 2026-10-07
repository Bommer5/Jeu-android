import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Identifiants de monétisation : lus depuis monetization.properties (non versionné)
// sinon on utilise les identifiants de TEST officiels de Google (aucun revenu, aucun risque de ban).
val monetization = Properties().apply {
    val f = rootProject.file("monetization.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun mon(key: String, default: String) = monetization.getProperty(key) ?: System.getenv(key) ?: default

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.bommer.stacktower"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bommer.stacktower"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"

        manifestPlaceholders["admobAppId"] = mon("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
        buildConfigField("String", "AD_BANNER_ID", "\"${mon("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/9214589741")}\"")
        buildConfigField("String", "AD_INTERSTITIAL_ID", "\"${mon("ADMOB_INTERSTITIAL_ID", "ca-app-pub-3940256099942544/1033173712")}\"")
        buildConfigField("String", "AD_REWARDED_ID", "\"${mon("ADMOB_REWARDED_ID", "ca-app-pub-3940256099942544/5224354917")}\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.google.android.play:review:2.0.2")

    // Monétisation
    implementation("com.google.android.gms:play-services-ads:24.5.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    implementation("com.android.billingclient:billing:8.0.0")

    testImplementation("junit:junit:4.13.2")
}
