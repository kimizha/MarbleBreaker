plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.kimizha.minigame"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.kimizha.minigame"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "2.1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
}
dependencies {
    implementation("androidx.activity:activity-ktx:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
