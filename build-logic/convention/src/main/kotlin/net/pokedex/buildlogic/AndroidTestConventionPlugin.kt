package net.pokedex.buildlogic

import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * A `com.android.test` module: an APK that drives another module's APK from outside.
 *
 * :baselineprofile is the only one. It exists because a device measurement said so
 * (docs/architecture.md §8), and it reaches :app through `targetProjectPath`, never
 * through a project dependency, so it can never become a path between features.
 */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<TestExtension> {
            configureAndroidCommon(this)
            defaultConfig {
                targetSdk = PokedexSdk.TARGET
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }
    }
}
