// Root build file: plugin versions only, no configuration. All module configuration
// lives in build-logic convention plugins -- see CLAUDE.md.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt)
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "app/src", "core/model/src", "core/data/src",
            "design-system/src", "feature/dex/src",
            "build-logic/convention/src",
        ),
    )
    parallel = true
}

/**
 * The design-usage rules, enforced rather than requested.
 *
 * docs/design-usage.md tells feature sessions that raw `Color(...)`, raw dp/sp and ad-hoc
 * TextStyles are forbidden -- everything comes from :design-system. A rule nobody checks is
 * a rule nobody follows, so this turns it into a build failure.
 *
 * It works by forbidding the *imports*. You cannot write `16.dp` without importing
 * `androidx.compose.ui.unit.dp`, and you cannot write `Color(0xFF...)` without importing
 * `Color` -- so the cheap check is also the complete one, and it needs no type resolution.
 *
 * Scoped to feature/ only. :design-system is where these types are supposed to be used.
 *
 * A genuine exception takes a @Suppress with a written reason, which is the point: it makes
 * the exception visible in review instead of invisible in a diff.
 */
val detektFeatureRules by tasks.registering(io.gitlab.arturbosch.detekt.Detekt::class) {
    group = "verification"
    description = "Fails a feature module that defines its own colours, spacing or text styles."
    buildUponDefaultConfig = false
    config.setFrom(files("$rootDir/config/detekt/feature-rules.yml"))
    // src only. Rooting the tree at feature/ pulls in feature/*/build/, which makes Gradle
    // infer a dependency on every AGP packaging task in the module and fail the build with
    // an implicit-dependency error the moment this runs alongside assembleDebug.
    source = fileTree("$rootDir/feature") {
        include("**/src/**/*.kt")
        exclude("**/build/**")
    }
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

tasks.named("detekt") { dependsOn(detektFeatureRules) }

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}
