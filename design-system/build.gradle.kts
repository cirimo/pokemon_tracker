plugins {
    alias(libs.plugins.pokedex.android.library)
    alias(libs.plugins.pokedex.android.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "net.pokedex.designsystem"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

/**
 * Deliberately empty of internal dependencies.
 *
 * The M1 contract (docs/design-decisions.md) says this module depends on no feature
 * module and not on :data or :domain. If you find yourself wanting a domain type here,
 * the component is taking on knowledge that belongs in a feature.
 */
dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
}
