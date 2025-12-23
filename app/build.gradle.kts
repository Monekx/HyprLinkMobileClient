import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Функция для автоинкремента версии
fun getVersionCodeAndIncrement(): Int {
    val versionPropsFile = file("version.properties")
    val versionProps = Properties()

    if (versionPropsFile.exists()) {
        versionProps.load(versionPropsFile.inputStream())
    }

    val code = versionProps.getProperty("VERSION_CODE", "0").toInt() + 1
    versionProps["VERSION_CODE"] = code.toString()
    versionProps.store(versionPropsFile.outputStream(), null)
    return code
}

val currentVersionCode = getVersionCodeAndIncrement()
val currentVersionName = "0.0.$currentVersionCode"

android {
    namespace = "com.monekx.hyprlink"
    compileSdk = 36 // Рекомендуется использовать стабильный SDK, если 36 еще в превью

    defaultConfig {
        applicationId = "com.monekx.hyprlink"
        minSdk = 26
        targetSdk = 36
        versionCode = currentVersionCode
        versionName = currentVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Настройка имени APK файла
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            val abi = output.getFilter(com.android.build.OutputFile.ABI) ?: "universal"
            outputFileName = "alpha-hyprlink-mobile-$currentVersionName-$abi.apk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.media:media:1.7.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.compose.material3:material3:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}