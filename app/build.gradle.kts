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
        versionCode = 1
        versionName = "0.1.0"
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
            isMinifyEnabled = false
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
}

dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("io.nayuki:qrcodegen:1.8.0")
}
