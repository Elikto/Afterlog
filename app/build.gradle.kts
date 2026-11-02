plugins {
    id("com.android.application")
}
android {
    namespace = "fr.afterlog.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "fr.afterlog.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.5.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("org.jsoup:jsoup:1.18.3")
}
