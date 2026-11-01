plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "de.einsatzleitstelle110"; compileSdk = 35
 defaultConfig { applicationId = "de.einsatzleitstelle110"; minSdk = 26; targetSdk = 35; versionCode = 5; versionName = "0.5.0-operator" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
