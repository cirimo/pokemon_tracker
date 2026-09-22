package net.pokedex.buildlogic

import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room, plus the schema export the dataset pipeline depends on.
 *
 * schemas/ is NOT a throwaway build artifact -- it is checked in, and
 * tools/dataset-pipeline reads the exported JSON to emit the DDL for the reference
 * database and the room_master_table identity hash that lets Room accept the asset.
 * See docs/dataset-pipeline.md.
 */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        dependencies {
            add("implementation", libs.findLibrary("androidx-room-runtime").get())
            add("implementation", libs.findLibrary("androidx-room-ktx").get())
            add("ksp", libs.findLibrary("androidx-room-compiler").get())
            add("androidTestImplementation", libs.findLibrary("androidx-room-testing").get())
        }
    }
}
