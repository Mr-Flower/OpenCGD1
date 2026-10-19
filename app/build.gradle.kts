import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// La release va firmata sempre con la stessa chiave, altrimenti Android rifiuta l'aggiornamento e chiede di
// disinstallare. In CI la chiave arriva dai secret (variabili OPENCGD1_*), in locale da keystore.properties.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun signingValue(env: String, property: String): String? =
    providers.environmentVariable(env).orNull ?: keystoreProperties.getProperty(property)

val releaseStoreFile = signingValue("OPENCGD1_STORE_FILE", "storeFile")

android {
    namespace = "it.cgd1.sveglia"
    compileSdk = 37

    defaultConfig {
        applicationId = "it.cgd1.sveglia"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.5.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("OPENCGD1_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("OPENCGD1_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("OPENCGD1_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Senza chiave (es. chi clona il repo) si firma con quella di debug: installabile, ma non aggiornabile
            // sopra una release ufficiale.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Genera locales_config.xml dalle cartelle values-xx: la lingua per-app compare anche nelle Impostazioni di Android 13+.
        generateLocaleConfig = true
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
        // Segnala il nome dell'app "OpenCGD1" come refuso di "OpenCGD!".
        disable += "Typos"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
