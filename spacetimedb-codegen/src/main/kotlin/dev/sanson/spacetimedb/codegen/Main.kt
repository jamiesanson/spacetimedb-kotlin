package dev.sanson.spacetimedb.codegen

import dev.sanson.spacetimedb.codegen.generator.TypeGenerator
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import java.io.File

/**
 * CLI entry point for SpacetimeDB Kotlin codegen.
 *
 * Usage:
 *   spacetimedb-codegen --schema <file> --out-dir <dir> --package <name>
 *
 * Reads a V10 module schema JSON file (from `spacetimedb-standalone extract-schema`)
 * and generates typed Kotlin source files.
 */
public fun main(args: Array<String>) {
    val config = parseArgs(args)

    val schemaJson = File(config.schemaFile).readText()
    val schema = ModuleSchema.fromJson(schemaJson)
    val generator = TypeGenerator(schema, config.packageName)

    val outDir = File(config.outDir)
    outDir.mkdirs()

    var count = 0

    // Generate custom type files
    for (file in generator.generateTypeFiles()) {
        file.writeTo(outDir)
        count++
    }

    // Generate table row files
    for (table in schema.publicTables) {
        val productType = schema.tableProductType(table)
        val file = generator.generateTableRowFile(table.sourceName, productType)
        file.writeTo(outDir)
        count++
    }

    println("Generated $count files in ${outDir.absolutePath}")
}

internal data class CliConfig(
    val schemaFile: String,
    val outDir: String,
    val packageName: String,
)

internal fun parseArgs(args: Array<String>): CliConfig {
    var schemaFile: String? = null
    var outDir: String? = null
    var packageName: String? = null

    val iter = args.iterator()
    while (iter.hasNext()) {
        when (val arg = iter.next()) {
            "--schema" -> schemaFile = iter.nextOrError("--schema requires a value")
            "--out-dir" -> outDir = iter.nextOrError("--out-dir requires a value")
            "--package" -> packageName = iter.nextOrError("--package requires a value")
            "--help", "-h" -> {
                printUsage()
                System.exit(0)
            }
            else -> error("Unknown argument: $arg. Use --help for usage.")
        }
    }

    return CliConfig(
        schemaFile = schemaFile ?: error("--schema is required"),
        outDir = outDir ?: error("--out-dir is required"),
        packageName = packageName ?: error("--package is required"),
    )
}

private fun Iterator<String>.nextOrError(message: String): String {
    if (!hasNext()) error(message)
    return next()
}

private fun printUsage() {
    println(
        """
        |SpacetimeDB Kotlin Codegen
        |
        |Usage:
        |  spacetimedb-codegen --schema <file> --out-dir <dir> --package <name>
        |
        |Options:
        |  --schema <file>    Path to V10 module schema JSON (from extract-schema)
        |  --out-dir <dir>    Output directory for generated Kotlin sources
        |  --package <name>   Package name for generated code
        |  --help, -h         Show this help
        """.trimMargin()
    )
}
