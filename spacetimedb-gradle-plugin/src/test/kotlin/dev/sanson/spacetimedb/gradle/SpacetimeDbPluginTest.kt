package dev.sanson.spacetimedb.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpacetimeDbPluginTest {

    @Test
    fun `generateSpacetimeTypes task is registered`() {
        val projectDir = createTestProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("generateSpacetimeTypes"), "Expected generateSpacetimeTypes task")
    }

    @Test
    fun `generates Kotlin files from schema`() {
        val projectDir = createTestProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateSpacetimeTypes", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateSpacetimeTypes")?.outcome)

        val generatedDir = File(projectDir, "build/generated/spacetimedb/kotlin")
        assertTrue(generatedDir.exists(), "Generated directory should exist")

        val generatedFiles = generatedDir.walkTopDown()
            .filter { it.extension == "kt" }
            .map { it.name }
            .toSet()

        // Verify key generated files from the test fixture
        assertTrue("Player.kt" in generatedFiles, "Expected Player.kt, got: $generatedFiles")
        assertTrue("Person.kt" in generatedFiles, "Expected Person.kt, got: $generatedFiles")
        assertTrue("RemoteTables.kt" in generatedFiles, "Expected RemoteTables.kt, got: $generatedFiles")
        assertTrue("RemoteReducers.kt" in generatedFiles, "Expected RemoteReducers.kt, got: $generatedFiles")
        assertTrue("Reducer.kt" in generatedFiles, "Expected Reducer.kt, got: $generatedFiles")
    }

    @Test
    fun `task is cacheable - UP-TO-DATE on second run`() {
        val projectDir = createTestProject()

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateSpacetimeTypes")
            .withPluginClasspath()
            .build()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateSpacetimeTypes")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateSpacetimeTypes")?.outcome)
    }

    @Test
    fun `extractSpacetimeSchema is skipped when wasmModule not set`() {
        val projectDir = createTestProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("extractSpacetimeSchema", "generateSpacetimeTypes")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SKIPPED, result.task(":extractSpacetimeSchema")?.outcome)
    }

    private fun createTestProject(): File {
        val projectDir = File.createTempFile("stdb-test-", "").apply {
            delete()
            mkdirs()
        }

        // Copy test fixture schema
        val fixtureStream = javaClass.classLoader.getResourceAsStream("module-test-v10.json")
            ?: SpacetimeDbPluginTest::class.java.getResourceAsStream("/module-test-v10.json")
            ?: error("Test fixture module-test-v10.json not found on classpath")

        File(projectDir, "schema.json").writeText(fixtureStream.bufferedReader().readText())

        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "test-project"
        """.trimIndent())

        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                id("dev.sanson.spacetimedb")
            }
            
            spacetimedb {
                schemaFile.set(file("schema.json"))
                packageName.set("com.example.test")
            }
        """.trimIndent())

        return projectDir
    }
}
