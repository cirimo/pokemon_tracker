plugins {
    alias(libs.plugins.pokedex.android.application)
    alias(libs.plugins.pokedex.android.compose)
    alias(libs.plugins.pokedex.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
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
 * The profile is generated on demand with a phone attached (see :baselineprofile), never
 * as part of a build, and checked in under src/main/generated/baselineProfiles/.
 * mergeIntoMain puts it where every non-debuggable variant picks it up.
 */
baselineProfile {
    mergeIntoMain = true
    automaticGenerationDuringBuild = false
}

/**
 * Works around a baseline profile plugin (1.5.0) bug that breaks the build on Windows.
 *
 * The plugin copies `release`'s Kotlin source dirs into the build types it synthesises
 * (nonMinifiedRelease, benchmarkRelease) by calling getSrcDirs() during finalizeDsl. On AGP
 * 8.13 that set is still one lazy Provider at that point, which resolves to a directory
 * literally named "provider(?)". On Linux that is merely a directory that does not exist;
 * on Windows `?` is illegal in a path and KSP fails every task of those variants with
 * "Illegal char <?>". The real release dirs are copied alongside it, so dropping the bogus
 * entry loses nothing. This callback is registered after the plugin's, so it runs after
 * the copy. Delete it once the plugin copies lazily.
 */
androidComponents {
    finalizeDsl { android ->
        // The synthetic build types get their own application id. Generating a profile
        // installs the build under test and uninstalls it afterwards; under the release id
        // that replaced the real install and deleted its catch records with it. A profile
        // records classes and methods, not the package, so it applies to net.pokedex
        // unchanged.
        android.buildTypes
            .matching { it.name.startsWith("nonMinified") || it.name.startsWith("benchmark") }
            .configureEach { applicationIdSuffix = ".profiling" }

        android.sourceSets
            .matching { it.name.startsWith("nonMinified") || it.name.startsWith("benchmark") }
            .configureEach {
                val dirs = kotlin as com.android.build.gradle.api.AndroidSourceDirectorySet
                dirs.setSrcDirs(dirs.srcDirs.filterNot { it.name == "provider(?)" })
            }
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
    // Already on the classpath through AndroidX, declared so it cannot quietly leave. It
    // matters only for an install without the .dm: `./gradlew installRelease` pushes the
    // .dm and ART compiles at install, while a bare `adb install` relies on this writing
    // the profile at first launch for the next background dexopt. docs/architecture.md §8.
    implementation(libs.androidx.profileinstaller)

    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit4)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
}
