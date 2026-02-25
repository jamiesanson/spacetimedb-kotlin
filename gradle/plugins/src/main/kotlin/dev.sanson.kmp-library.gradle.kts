plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("dev.drewhamilton.poko")
}

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        // KLib validation requires host compilers for each native target. CI runs on Ubuntu,
        // which can't build Apple targets. Since all code is in commonMain, the JVM API dump
        // is sufficient to catch public API changes.
        enabled = false
    }
}

kotlin {
    explicitApi()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

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
