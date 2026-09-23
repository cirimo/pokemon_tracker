plugins {
    alias(libs.plugins.pokedex.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.pokedex.feature.dex"
}

dependencies {
    // Sprites are loaded here, not in :design-system: the slot decides how a sprite is
    // drawn, the feature decides where the file comes from. See BoxSlot's KDoc.
    implementation(libs.coil.compose)
    // The search filter is saved across process death as one JSON string.
    implementation(libs.kotlinx.serialization.json)
}
