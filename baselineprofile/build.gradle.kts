plugins {
    alias(libs.plugins.pokedex.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * Generates :app's baseline profile by driving the release app on a real device.
 *
 * On demand, like the dataset: `./gradlew :app:generateReleaseBaselineProfile` with a phone
 * connected. CI has no device and a normal build never runs this. The output is checked in
 * under app/src/main/generated/baselineProfiles/. docs/architecture.md §8 has when and why.
 *
 * :app is reached through targetProjectPath, not a project dependency. Keep it that way: CI
 * fails this file if it gains a project dependency, so the module can never become a path
 * from one feature to another.
 */
android {
    namespace = "net.pokedex.baselineprofile"
    targetProjectPath = ":app"
}

baselineProfile {
    // The phone on wireless adb. No Gradle managed devices: an emulator's profile would
    // describe the emulator's JIT, and the measurement that justified this module is the
    // phone's.
    useConnectedDevices = true
}

/**
 * The plugin makes this module's `assemble` depend on collecting the profile, so a plain
 * `./gradlew build` would run the journey on whatever phone is attached (or fail with none)
 * and overwrite the checked-in profile. Generation is on demand, like the dataset: the
 * device tasks run only when a baseline profile task was asked for by name.
 */
val generationRequested = gradle.startParameter.taskNames.any { it.contains("BaselineProfile") }
tasks.matching { it.name.startsWith("connected") || it.name.startsWith("collect") }.configureEach {
    // A plain boolean rather than onlyIf { }: the lambda would capture this script, which
    // the configuration cache cannot serialise.
    enabled = generationRequested
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
}
