plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.srik.shortstracker"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        // Public id from 0.4 on (renamed from com.srik.shortstracker). It can never change once on a store.
        // The Kotlin package (namespace) still says shortstracker; that's internal and fine.
        applicationId = "com.srik.thumbtally"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.1"
    }

    // The owner's upload key lives outside the repo; its password is read from the macOS Keychain
    // (item "thumb-tally-keystore"), so it is never in a file, the repo or a log. Anyone else building
    // from source gets a debug-signed release instead.
    val keystore = file("${System.getProperty("user.home")}/.config/thumb-tally/upload.jks")
    val keystorePassword: String? = if (!keystore.exists()) null else runCatching {
        val result = providers.exec {
            commandLine("security", "find-generic-password", "-s", "thumb-tally-keystore", "-w")
            isIgnoreExitValue = true
        }
        if (result.result.get().exitValue == 0) result.standardOutput.asText.get().trim().ifEmpty { null } else null
    }.getOrNull()

    signingConfigs {
        if (keystorePassword != null) {
            create("upload") {
                storeFile = keystore
                storePassword = keystorePassword
                keyAlias = "thumbtally"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to the real app on the same phone.
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
