package dev.sanson.spacetimedb.codegen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MainTest {

    private val fixture: String by lazy {
        this::class.java.getResource("/module-test-v10.json")!!.readText()
    }

    @Test
    fun `parses CLI arguments`() {
        val config = parseArgs(arrayOf("--schema", "schema.json", "--out-dir", "out", "--package", "com.example"))
        assertEquals("schema.json", config.schemaFile)
        assertEquals("out", config.outDir)
        assertEquals("com.example", config.packageName)
    }

    @Test
    fun `fails on missing required args`() {
        assertFailsWith<IllegalStateException> {
            parseArgs(arrayOf("--schema", "schema.json"))
        }
    }

    @Test
    fun `fails on unknown argument`() {
        assertFailsWith<IllegalStateException> {
            parseArgs(arrayOf("--schema", "schema.json", "--unknown", "value"))
        }
    }

    @Test
    fun `end-to-end generates files to directory`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "codegen-test-${System.nanoTime()}")
        val schemaFile = File(tempDir, "schema.json")

        try {
            tempDir.mkdirs()
            schemaFile.writeText(fixture)

            main(
                arrayOf(
                    "--schema", schemaFile.absolutePath,
                    "--out-dir", File(tempDir, "generated").absolutePath,
                    "--package", "com.example.test",
                )
            )

            val genDir = File(tempDir, "generated/com/example/test")
            assertTrue(genDir.exists(), "Generated directory should exist")

            val files = genDir.listFiles()?.filter { it.extension == "kt" } ?: emptyList()
            assertTrue(files.isNotEmpty(), "Should generate at least one .kt file")

            // Verify generated files contain valid content
            for (file in files) {
                val content = file.readText()
                assertTrue(content.contains("package com.example.test"), "File ${file.name} missing package")
                assertTrue(content.contains("@Serializable") || content.contains("Serializable"), "File ${file.name} missing @Serializable")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
