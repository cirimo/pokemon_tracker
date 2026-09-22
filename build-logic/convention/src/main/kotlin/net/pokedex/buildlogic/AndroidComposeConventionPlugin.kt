package net.pokedex.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * The single place Compose is configured, shared by :design-system, :app and every
 * feature module -- required by the M1 contract in docs/design-decisions.md.
 *
 * Pulls the Compose BOM rather than pinning artifact versions. The BOM chosen in the
 * catalog is the one that resolves material3 to the 1.4.0 stable line, NOT the
 * 1.5.0-alpha Expressive line.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val android = extensions.findByName("android") as? CommonExtension<*, *, *, *, *, *>
            ?: error("pokedex.android.compose must be applied after an Android plugin")
        android.buildFeatures.compose = true

        dependencies {
            val bom = platform(libs.findLibrary("androidx-compose-bom").get())
            add("implementation", bom)
            add("testImplementation", bom)
            add("androidTestImplementation", bom)

            add("implementation", libs.findLibrary("androidx-compose-ui").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("androidx-compose-material3").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
            add("androidTestImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
        }
    }
}
