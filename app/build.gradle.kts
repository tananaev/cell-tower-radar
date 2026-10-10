plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.tananaev.celltowerradar"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tananaev.celltowerradar"
        minSdk = 23
        targetSdk = 37
        versionCode = 21
        versionName = "3.7"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("KEYSTORE_FILE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    flavorDimensions += "default"
    productFlavors {
        create("regular") {
            isDefault = true
            extra["enableCrashlytics"] = false
        }
        create("google")
    }

    buildTypes {
        getByName("debug") {
            resValue("string", "ad_unit_id", "ca-app-pub-3940256099942544/6300978111")
        }
        getByName("release") {
            resValue("string", "ad_unit_id", "ca-app-pub-9061647223840223/7068051826")
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.play.services.maps)
    implementation(libs.gson)
    implementation(libs.okhttp)
    "googleImplementation"(platform(libs.firebase.bom))
    "googleImplementation"(libs.firebase.analytics)
    "googleImplementation"(libs.firebase.crashlytics)
    "googleImplementation"(libs.play.services.ads)
    "googleImplementation"(libs.play.review.ktx)
}
