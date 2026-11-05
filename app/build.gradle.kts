plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "de.einsatzleitstelle110"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.einsatzleitstelle110"
        minSdk = 26
        targetSdk = 35
        versionCode = 114
        versionName = "0.13.0-notruf-preview"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
}