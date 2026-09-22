plugins {
    alias(libs.plugins.pokedex.android.application)
    alias(libs.plugins.pokedex.android.compose)
    alias(libs.plugins.pokedex.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.pokedex"

    defaultConfig {
        applicationId = "net.pokedex"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        // The app needs BuildConfig: the backup file records the version it came from.
        buildConfig = true
    }

    buildTypes {
        debug {
            // Suffixed so debug and release coexist on the phone. The debug build is
            // also the one carrying the design-system gallery launcher.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("releaseLocal")
                ?: signingConfigs.getByName("debug")
        }
    }

    androidResources {
        // reference.db and the sprites are already compressed. Leaving them to aapt
        // costs build time and saves nothing.
        noCompress += listOf("db", "webp")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

/**
 * Release signing, from ~/.gradle/gradle.properties -- never from the repo.
 *
 * Set pokedexKeystorePath / pokedexKeystorePassword / pokedexKeyAlias /
 * pokedexKeyPassword there and `./gradlew installRelease` signs properly. Without
 * them the release build falls back to the debug key, which is fine for a personal
 * sideload and obvious enough that it will not be mistaken for a real signing setup.
 */
val keystorePath: String? = providers.gradleProperty("pokedexKeystorePath").orNull
if (keystorePath != null) {
    android.signingConfigs.create("releaseLocal") {
        storeFile = file(keystorePath)
        storePassword = providers.gradleProperty("pokedexKeystorePassword").orNull
        keyAlias = providers.gradleProperty("pokedexKeyAlias").orNull
        keyPassword = providers.gradleProperty("pokedexKeyPassword").orNull
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":design-system"))
    implementation(project(":feature:dex"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coil.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
}
