package dev.sanson.spacetimedb.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpacetimeDbPluginTest {

    @Test
    fun `tasks are registered`() {
        val projectDir = createTestProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("buildSpacetimeModule"), "Expected buildSpacetimeModule task")
        assertTrue(result.output.contains("generateSpacetimeTypes"), "Expected generateSpacetimeTypes task")
    }

    @Test
    fun `generates Kotlin files from schema`() {
        val projectDir = createTestProject()

        // Pre-populate the schema file so we can test generation without spacetime CLI
        val schemaDir = File(projectDir, "build/generated/spacetimedb")
        schemaDir.mkdirs()
        copyFixture(File(schemaDir, "schema.json"))

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateSpacetimeTypes", "--stacktrace", "-x", "buildSpacetimeModule")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateSpacetimeTypes")?.outcome)

        val generatedDir = File(projectDir, "build/generated/spacetimedb/kotlin")
        assertTrue(generatedDir.exists(), "Generated directory should exist")

        val generatedFiles = generatedDir.walkTopDown()
            .filter { it.extension == "kt" }
            .map { it.name }
            .toSet()

        assertTrue("Player.kt" in generatedFiles, "Expected Player.kt, got: $generatedFiles")
        assertTrue("Person.kt" in generatedFiles, "Expected Person.kt, got: $generatedFiles")
        assertTrue("RemoteTables.kt" in generatedFiles, "Expected RemoteTables.kt, got: $generatedFiles")
        assertTrue("RemoteReducers.kt" in generatedFiles, "Expected RemoteReducers.kt, got: $generatedFiles")
        assertTrue("Reducer.kt" in generatedFiles, "Expected Reducer.kt, got: $generatedFiles")
    }

    @Test
    fun `task is cacheable - UP-TO-DATE on second run`() {
        val projectDir = createTestProject()

        val schemaDir = File(projectDir, "build/generated/spacetimedb")
        schemaDir.mkdirs()
        copyFixture(File(schemaDir, "schema.json"))

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateSpacetimeTypes", "-x", "buildSpacetimeModule")
            .withPluginClasspath()
            .build()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateSpacetimeTypes", "-x", "buildSpacetimeModule")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateSpacetimeTypes")?.outcome)
    }

    @Test
    fun `extension accepts buildOptions`() {
        val projectDir = createTestProjectWithBuildOptions()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("buildSpacetimeModule"), "Expected buildSpacetimeModule task")
    }

    private fun copyFixture(target: File) {
        val stream = javaClass.classLoader.getResourceAsStream("module-test-v10.json")
            ?: error("Test fixture module-test-v10.json not found on classpath")
        target.writeText(stream.bufferedReader().readText())
    }

    private fun createTestProject(): File {
        val projectDir = File.createTempFile("stdb-test-", "").apply {
            delete()
            mkdirs()
        }

        // Create a fake module directory (won't actually build, but satisfies config)
        File(projectDir, "server").mkdirs()

        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "test-project"
        """.trimIndent())

        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                id("dev.sanson.spacetimedb")
            }
            
            spacetimedb {
                modulePath.set(file("server"))
                packageName.set("com.example.test")
            }
        """.trimIndent())

        return projectDir
    }

    private fun createTestProjectWithBuildOptions(): File {
        val projectDir = File.createTempFile("stdb-test-", "").apply {
            delete()
            mkdirs()
        }

        File(projectDir, "server").mkdirs()

        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "test-project"
        """.trimIndent())

        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                id("dev.sanson.spacetimedb")
            }
            
            spacetimedb {
                modulePath.set(file("server"))
                packageName.set("com.example.test")
                buildOptions.set(listOf("--debug"))
            }
        """.trimIndent())

        return projectDir
    }
}
