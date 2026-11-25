plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mindosc.freenux"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mindosc.freenux"
        minSdk = 28
        // targetSdk 28: επιτρέπει εκτέλεση των Linux binaries από τον φάκελο της εφαρμογής (όπως το Termux)
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    // Σταθερό κλειδί υπογραφής: τα νέα APK εγκαθίστανται πάνω από τα παλιά
    signingConfigs {
        create("freenux") {
            storeFile = file("freenux.keystore")
            storePassword = "freenux"
            keyAlias = "freenux"
            keyPassword = "freenux"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("freenux") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("freenux")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs { useLegacyPackaging = true } // να αποσυμπιέζονται τα libproot*.so ώστε να εκτελούνται
        resources { excludes += listOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES") }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
}
