import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val secrets = Properties().apply {
    val file = rootProject.file("api.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String) = "\"${secrets.getProperty(name, "")}\""

android {
    namespace = "ai.bureadcar.app"
    compileSdk = 35
    useLibrary("android.car")

    defaultConfig {
        applicationId = "ai.bureadcar.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "RONE_API_KEY", secret("RONE_API_KEY"))
        buildConfigField("String", "VWORLD_API_KEY", secret("VWORLD_API_KEY"))
        buildConfigField("String", "ECOS_API_KEY", secret("ECOS_API_KEY"))
        // Portal key in its URL-encoded form; it goes into URLs as is.
        buildConfigField("String", "DATA_GO_KR_API_KEY", secret("DATA_GO_KR_API_KEY"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    // Schedule JSON is exercised in JVM tests, which need a working org.json.
    testImplementation("org.json:json:20240303")
}
