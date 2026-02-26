package dev.sanson.spacetimedb.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Gradle plugin for SpacetimeDB Kotlin code generation.
 *
 * Registers a `spacetimedb` extension and tasks that build a SpacetimeDB module,
 * extract its schema, and generate typed Kotlin sources. Generated sources are
 * automatically wired into the Kotlin compilation.
 *
 * The plugin also adds `spacetimedb-core` (and transitively `spacetimedb-bsatn`)
 * as a dependency, so consumers don't need to declare it manually.
 *
 * Usage:
 * ```kotlin
 * plugins {
 *     id("dev.sanson.spacetimedb")
 * }
 *
 * spacetimedb {
 *     modulePath.set(file("server"))
 *     packageName.set("com.example.mymodule")
 * }
 * ```
 */
public class SpacetimeDbPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("spacetimedb", SpacetimeDbExtension::class.java)

        val generatedDir = project.layout.buildDirectory.dir("generated/spacetimedb/kotlin")
        val schemaFile = project.layout.buildDirectory.file("generated/spacetimedb/schema.json")

        // Build module and extract schema
        val buildTask = project.tasks.register("buildSpacetimeModule", BuildModuleTask::class.java) { task ->
            task.modulePath.set(extension.modulePath)
            task.buildOptions.set(extension.buildOptions)
            task.schemaFile.set(schemaFile)
        }

        // Generate Kotlin sources from schema
        val generateTask = project.tasks.register("generateSpacetimeTypes", GenerateSpacetimeTypesTask::class.java) { task ->
            task.schemaFile.set(buildTask.flatMap { it.schemaFile })
            task.packageName.set(extension.packageName)
            task.outputDirectory.set(generatedDir)
            task.dependsOn(buildTask)
        }

        val sdkVersion = javaClass.`package`.implementationVersion ?: project.findProperty("dev.sanson.spacetimedb.version") as? String

        // Kotlin JVM: add generated sources + SDK dependency
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            project.extensions.getByType(SourceSetContainer::class.java)
                .getByName("main")
                .java
                .srcDir(generateTask.flatMap { it.outputDirectory })

            if (sdkVersion != null) {
                project.dependencies.add("implementation", "dev.sanson.spacetimedb:spacetimedb-core:$sdkVersion")
            }
        }

        // Kotlin Multiplatform: add generated sources to commonMain + SDK dependency
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val kmpExt = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kmpExt.sourceSets.getByName("commonMain") { sourceSet ->
                sourceSet.kotlin.srcDir(generateTask.flatMap { it.outputDirectory })
            }

            if (sdkVersion != null) {
                kmpExt.sourceSets.getByName("commonMain") { sourceSet ->
                    sourceSet.dependencies {
                        implementation("dev.sanson.spacetimedb:spacetimedb-core:$sdkVersion")
                    }
                }
            }
        }

        // Make compileKotlin depend on generation
        project.tasks.configureEach { task ->
            if (task.name == "compileKotlin" || task.name == "compileKotlinJvm" || task.name == "compileKotlinMetadata") {
                task.dependsOn(generateTask)
            }
        }
    }
}
