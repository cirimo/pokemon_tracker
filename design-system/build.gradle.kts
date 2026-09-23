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
    // Icons are an implementation detail on purpose: PokedexIcons re-exports plain
    // ImageVectors, so a feature module can use them without material-icons on its own
    // classpath -- which is what makes "import from PokedexIcons, never from
    // androidx.compose.material.icons" enforced by the compiler rather than by review.
    implementation(libs.androidx.compose.material.icons.core)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
}

/**
 * The one command that checks the design system, and that you can actually run.
 *
 * `./gradlew :design-system:designCheck`
 *
 * Covers, in order of how quietly each one breaks:
 *
 *  - ContrastTest      -- every declared token pair, both themes, against its WCAG bar
 *  - ContrastTest      -- all 18 type badges, plus the uniformity of the OKLCH ramp
 *  - AccessibilityTest -- 48dp touch targets and TalkBack sentences
 *  - BoxGridScrollingParentTest -- the container contracts a screenshot cannot see
 *
 * ## Why screenshot verification is deliberately NOT in here
 *
 * It used to be, and that was wrong in a way that took a round trip through CI to see.
 *
 * Roborazzi compares pixels exactly and Robolectric does not render identically across
 * operating systems, so a golden is only valid on the platform that recorded it. Goldens
 * recorded on Windows failed on ubuntu-latest; once they were re-recorded on CI, they
 * failed on Windows. A gate that cannot pass on a developer machine is not a gate, it is
 * noise that people learn to ignore.
 *
 * So the split follows the actual property: everything above is platform-independent and
 * runs anywhere, and `verifyRoborazziDebug` is a CI-only gate that build.yml runs
 * alongside this one. Record goldens with the `record screenshots` workflow --
 * docs/design-usage.md has the procedure.
 *
 * Running `verifyRoborazziDebug` locally is expected to fail, and that failure means
 * nothing. Use `recordRoborazziDebug` to look at what a component renders.
 */
tasks.register("designCheck") {
    group = "verification"
    description = "Contrast, touch targets, semantics and container contracts, in both themes."
    dependsOn("testDebugUnitTest")
}
