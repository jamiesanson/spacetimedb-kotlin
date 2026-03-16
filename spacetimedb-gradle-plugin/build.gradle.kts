plugins {
    id("dev.sanson.module")
    `java-gradle-plugin`
}

kotlin { explicitApi() }

// Generate a version class at build time so the plugin knows its own version
// without requiring gradle.properties in the consuming project.
val generateVersion =
    tasks.register("generateVersionClass") {
        val versionProp = project.version.toString()
        val outputDir = layout.buildDirectory.dir("generated/version/kotlin")
        outputs.dir(outputDir)
        inputs.property("version", versionProp)

        doLast {
            val dir = outputDir.get().asFile.resolve("dev/sanson/spacetimedb/gradle")
            dir.mkdirs()
            dir.resolve("SpacetimeDbBuildConfig.kt")
                .writeText(
                    """
            |package dev.sanson.spacetimedb.gradle
            |
            |internal object SpacetimeDbBuildConfig {
            |    const val VERSION: String = "$versionProp"
            |}
            """
                        .trimMargin()
                )
        }
    }

sourceSets.main { java.srcDir(generateVersion.map { it.outputs.files.singleFile }) }

tasks.named("compileKotlin") { dependsOn(generateVersion) }

dependencies {
    implementation(project(":spacetimedb-codegen"))
    implementation(libs.kotlinpoet)
    compileOnly(
        libs.plugins.kotlin.jvm.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
    )
    compileOnly(
        libs.plugins.kotlin.multiplatform.map {
            "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
        }
    )

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
