import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from keystore.properties (not in git):
//   storeFile=/path/to/release.jks  storePassword=...  keyAlias=...  keyPassword=...
// Without it, release builds are signed with the debug key.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties()
if (keystoreFile.exists()) {
    keystoreFile.inputStream().use { input -> keystore.load(input) }
}

android {
    namespace = "com.glasshome.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.glasshome.tv"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.4.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: smaller dex (most of it is the Kotlin library and NanoHTTPD) and
            // faster code. Activities and services are kept through the manifest.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("io.nayuki:qrcodegen:1.8.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("com.google.zxing:core:3.5.3")
}
