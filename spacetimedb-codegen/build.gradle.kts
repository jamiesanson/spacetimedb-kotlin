plugins {
    id("dev.sanson.module")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

kotlin { explicitApi() }

application { mainClass.set("dev.sanson.spacetimedb.codegen.MainKt") }

publishing { publications { create<MavenPublication>("maven") { from(components["java"]) } } }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinpoet)
    implementation(libs.clikt)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.compile.testing)
    // SDK types needed by compile-testing to verify generated code
    testImplementation(project(":spacetimedb-core"))
    testImplementation(project(":spacetimedb-bsatn"))
}
