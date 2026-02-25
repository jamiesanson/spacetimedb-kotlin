plugins {
    id("dev.sanson.module")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

kotlin {
    explicitApi()
}

application {
    mainClass.set("dev.sanson.spacetimedb.codegen.MainKt")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinpoet)

    testImplementation(libs.kotlin.test)
}
