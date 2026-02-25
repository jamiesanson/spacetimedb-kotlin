plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("dev.drewhamilton.poko")
}

kotlin {
    jvm()
    js(IR) {
        browser()
        nodejs()
    }

    // Tier 1
    macosX64()
    macosArm64()
    iosSimulatorArm64()
    iosArm64()

    // Tier 2
    linuxX64()
    linuxArm64()

    // Tier 3
    mingwX64()
}
