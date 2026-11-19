import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing credentials live in keystore.properties, which is gitignored. Without it (a fresh
// clone, CI without secrets) the release build still runs, it just stays unsigned.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "app.musiku"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.musiku"
        // Android 7.0. The app has three capability tiers, decided at runtime:
        //   33+ AGSL liquid metal + real blur, 31-32 gradient metal + blur, <31 gradient metal +
        //   frosted fills (Haze falls back to a scrim, so the glass surfaces stop being see-through).
        // NewPipeExtractor uses java.time, which is why desugaring is on below.
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "0.14"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time exists from API 26 and NewPipeExtractor uses it, so the desugared copy has to
        // ship for API 24/25.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    // 1.7.5 ui/foundation — the exact versions Haze 1.1.0 is built against.
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // No material-icons-extended: every glyph is a hand-ported MIcons/MMenuIcons vector, so the
    // extended icon set (100 MB+ of dead resources) would only bloat the APK.
    // liquid glass: real backdrop blur (RenderEffect) for the functional layer
    implementation("dev.chrisbanes.haze:haze:1.1.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // player: Media3 = background playback + lock screen / notification controls
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    // network + stream extraction
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // YouTube keeps changing; if playback breaks, bump this tag first.
    // v0.24.6 dies with "The page needs to be reloaded." — YouTube changed the
    // player response. v0.27.1 resolves audio streams again (verified on JVM).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}
