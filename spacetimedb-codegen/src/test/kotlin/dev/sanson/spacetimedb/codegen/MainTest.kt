package dev.sanson.spacetimedb.codegen

import com.github.ajalt.clikt.testing.test
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class MainTest {

    private val fixture: String by lazy {
        this::class.java.getResource("/module-test-v10.json")!!.readText()
    }

    @Test
    fun `shows help text`() {
        val result = GenerateCommand().test("--help")
        assertContains(result.output, "spacetimedb-codegen")
        assertContains(result.output, "--schema")
        assertContains(result.output, "--out-dir")
        assertContains(result.output, "--package")
    }

    @Test
    fun `fails on missing required args`() {
        val result = GenerateCommand().test("")
        assertTrue(result.statusCode != 0)
    }

    @Test
    fun `end-to-end generates files to directory`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "codegen-test-${System.nanoTime()}")
        val schemaFile = File(tempDir, "schema.json")

        try {
            tempDir.mkdirs()
            schemaFile.writeText(fixture)

            val result = GenerateCommand().test(
                "--schema ${schemaFile.absolutePath} " +
                    "--out-dir ${File(tempDir, "generated").absolutePath} " +
                    "--package com.example.test"
            )

            assertTrue(result.statusCode == 0, "Expected success, got: ${result.output}")
            assertContains(result.output, "Generated")

            val genDir = File(tempDir, "generated/com/example/test")
            assertTrue(genDir.exists(), "Generated directory should exist")

            val files = genDir.listFiles()?.filter { it.extension == "kt" } ?: emptyList()
            assertTrue(files.isNotEmpty(), "Should generate at least one .kt file")

            for (file in files) {
                val content = file.readText()
                assertContains(content, "package com.example.test")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
