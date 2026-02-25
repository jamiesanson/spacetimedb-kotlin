plugins {
    id("dev.sanson.module")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
}
