package dev.sanson.spacetimedb.codegen

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import dev.sanson.spacetimedb.codegen.generator.TypeGenerator
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

/**
 * CLI entry point for SpacetimeDB Kotlin codegen.
 *
 * Reads a V10 module schema JSON file (from `spacetimedb-standalone extract-schema`)
 * and generates typed Kotlin source files.
 */
public class GenerateCommand : CliktCommand(
    name = "spacetimedb-codegen",
) {
    override fun help(context: Context): String =
        "Generate typed Kotlin sources from a SpacetimeDB module schema."

    private val schema: Path by option("--schema", help = "Path to V10 module schema JSON")
        .path(mustExist = true, canBeDir = false)
        .required()

    private val outDir: Path by option("--out-dir", help = "Output directory for generated sources")
        .path()
        .required()

    private val packageName: String by option("--package", help = "Package name for generated code")
        .required()

    override fun run() {
        val schemaJson = schema.readText()
        val moduleSchema = ModuleSchema.fromJson(schemaJson)
        val generator = TypeGenerator(moduleSchema, packageName)

        outDir.createDirectories()
        val outFile = outDir.toFile()

        var count = 0

        for (file in generator.generateTypeFiles()) {
            file.writeTo(outFile)
            count++
        }

        for (table in moduleSchema.publicTables) {
            val productType = moduleSchema.tableProductType(table)
            val file = generator.generateTableRowFile(table.sourceName, productType)
            file.writeTo(outFile)
            count++
        }

        echo("Generated $count files in ${outDir.toAbsolutePath()}")
    }
}

public fun main(args: Array<String>) {
    GenerateCommand().main(args)
}
