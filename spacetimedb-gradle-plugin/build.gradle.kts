plugins {
    id("dev.sanson.module")
    `java-gradle-plugin`
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(project(":spacetimedb-codegen"))
    implementation(libs.kotlinpoet)
    compileOnly(libs.plugins.kotlin.jvm.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" })
    compileOnly(libs.plugins.kotlin.multiplatform.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" })

    testImplementation(libs.kotlin.test)
    testImplementation(gradleTestKit())
}

fun Provider<PluginDependency>.map(transform: (PluginDependency) -> String): Provider<String> =
    this.map(transform)

gradlePlugin {
    plugins {
        create("spacetimedb") {
            id = "dev.sanson.spacetimedb"
            implementationClass = "dev.sanson.spacetimedb.gradle.SpacetimeDbPlugin"
        }
    }
}
