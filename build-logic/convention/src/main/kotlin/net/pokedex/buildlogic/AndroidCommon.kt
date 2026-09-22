package net.pokedex.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * Configuration shared by every Android module, application or library.
 * Anything that differs between the two belongs in the calling plugin, not here.
 */
internal fun Project.configureAndroidCommon(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = PokedexSdk.COMPILE

        defaultConfig {
            minSdk = PokedexSdk.MIN
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            isCoreLibraryDesugaringEnabled = true
        }

        // Convention: src/<variant>/kotlin, never src/<variant>/java.
        sourceSets.configureEach {
            java.srcDirs("src/$name/kotlin")
        }

        lint {
            abortOnError = true
            checkDependencies = true
            // The toolchain is pinned as a set for reasons recorded in
            // docs/adr/0008-toolchain-baseline.md, and these two checks do nothing but
            // report that newer versions exist. Silencing them here, once, beats a
            // per-line suppression in the version catalog that hides real findings.
            disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
        }

        testOptions {
            unitTests {
                isIncludeAndroidResources = true
                isReturnDefaultValues = true
            }
        }
    }

    extensions.configure(KotlinAndroidProjectExtension::class.java) {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
        }
    }

    dependencies {
        add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk-libs").get())
    }
}
