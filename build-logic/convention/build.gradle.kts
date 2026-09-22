plugins {
    `kotlin-dsl`
}

group = "net.pokedex.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "pokedex.android.application"
            implementationClass = "net.pokedex.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "pokedex.android.library"
            implementationClass = "net.pokedex.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "pokedex.android.compose"
            implementationClass = "net.pokedex.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "pokedex.android.hilt"
            implementationClass = "net.pokedex.buildlogic.HiltConventionPlugin"
        }
        register("androidRoom") {
            id = "pokedex.android.room"
            implementationClass = "net.pokedex.buildlogic.RoomConventionPlugin"
        }
        register("androidFeature") {
            id = "pokedex.android.feature"
            implementationClass = "net.pokedex.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "pokedex.jvm.library"
            implementationClass = "net.pokedex.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
