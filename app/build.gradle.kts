plugins {
    id("com.android.application")
}
val persistentKeystore = System.getenv("AFTERLOG_KEYSTORE_FILE")
android {
    namespace = "fr.afterlog.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "fr.afterlog.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "0.10.0"
    }
    signingConfigs {
        if (!persistentKeystore.isNullOrBlank()) {
            create("afterlogPersistent") {
                storeFile = file(persistentKeystore)
                storePassword = System.getenv("AFTERLOG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AFTERLOG_KEY_ALIAS")
                keyPassword = System.getenv("AFTERLOG_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!persistentKeystore.isNullOrBlank())
                signingConfig = signingConfigs.getByName("afterlogPersistent")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("androidx.work:work-runtime:2.12.0")
}
