plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "de.einsatzleitstelle110"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.einsatzleitstelle110"
        minSdk = 26
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0-native"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Explicit native-only assets: no index.html, no CSS, no JS in the APK.
    sourceSets.getByName("main") {
        assets.setSrcDirs(listOf("src/main/native_assets", "src/main/assets/images"))
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
}