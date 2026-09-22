package net.pokedex.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * A feature module. This plugin encodes the dependency rule from CLAUDE.md so it cannot
 * be forgotten: a feature may depend on :core:model, :core:data and :design-system, and
 * on nothing else internal. Never on another feature.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("pokedex.android.library")
        pluginManager.apply("pokedex.android.compose")
        pluginManager.apply("pokedex.android.hilt")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data"))
            add("implementation", project(":design-system"))

            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())

            add("testImplementation", libs.findLibrary("junit4").get())
            add("testImplementation", libs.findLibrary("truth").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
