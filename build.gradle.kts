import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3"
}

val env = Properties().apply {
    val f = rootProject.file(".env")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.mpdkiosk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mpdkiosk"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "MPD_HOST", "\"${env.getProperty("MPD_HOST", "127.0.0.1")}\"")
        buildConfigField("int", "MPD_PORT", env.getProperty("MPD_PORT", "6600"))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
