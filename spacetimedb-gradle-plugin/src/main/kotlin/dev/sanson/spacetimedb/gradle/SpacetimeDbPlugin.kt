package dev.sanson.spacetimedb.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

/**
 * Gradle plugin for SpacetimeDB Kotlin code generation.
 *
 * Registers a `spacetimedb` extension and tasks that build a SpacetimeDB module,
 * extract its schema, and generate typed Kotlin sources. Generated sources are
 * automatically added to the main source set.
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

        // Wire generated sources into compilation
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            project.extensions.getByType(SourceSetContainer::class.java)
                .getByName("main")
                .java
                .srcDir(generateTask.flatMap { it.outputDirectory })
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.afterEvaluate {
                project.extensions.findByName("kotlin")?.let { kotlinExt ->
                    try {
                        val method = kotlinExt.javaClass.getMethod("sourceSets")
                        @Suppress("UNCHECKED_CAST")
                        val sourceSets = method.invoke(kotlinExt) as org.gradle.api.NamedDomainObjectContainer<*>
                        sourceSets.findByName("commonMain")?.let { sourceSet ->
                            val kotlinMethod = sourceSet.javaClass.getMethod("getKotlin")
                            val kotlinSourceDir = kotlinMethod.invoke(sourceSet)
                            val srcDirMethod = kotlinSourceDir.javaClass.getMethod("srcDir", Any::class.java)
                            srcDirMethod.invoke(kotlinSourceDir, generateTask.flatMap { it.outputDirectory })
                        }
                    } catch (_: Exception) {
                        project.logger.warn("SpacetimeDB: could not wire generated sources into KMP commonMain")
                    }
                }
            }
        }

        // Make compileKotlin depend on generation
        project.tasks.configureEach { task ->
            if (task.name == "compileKotlin" || task.name == "compileKotlinJvm") {
                task.dependsOn(generateTask)
            }
        }
    }
}
