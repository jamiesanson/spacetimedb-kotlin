package dev.sanson.spacetimedb.codegen

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import dev.sanson.spacetimedb.codegen.generator.ModuleGenerator
import dev.sanson.spacetimedb.codegen.generator.ReducerGenerator
import dev.sanson.spacetimedb.codegen.generator.TableHandleGenerator
import dev.sanson.spacetimedb.codegen.generator.TypeGenerator
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

/**
 * CLI entry point for SpacetimeDB Kotlin codegen.
 *
 * Reads a V10 module schema JSON file (from `spacetimedb-standalone extract-schema`) and generates
 * typed Kotlin source files.
 */
public class GenerateCommand : CliktCommand(name = "spacetimedb-codegen") {
    override fun help(context: Context): String =
        "Generate typed Kotlin sources from a SpacetimeDB module schema."

    private val schema: Path by
        option("--schema", help = "Path to V10 module schema JSON")
            .path(mustExist = true, canBeDir = false)
            .required()

    private val outDir: Path by
        option("--out-dir", help = "Output directory for generated sources").path().required()

    private val packageName: String by
        option("--package", help = "Package name for generated code").required()

    override fun run() {
        val schemaJson = schema.readText()
        val moduleSchema = ModuleSchema.fromJson(schemaJson)
        val typeGenerator = TypeGenerator(moduleSchema, packageName)
        val tableHandleGenerator = TableHandleGenerator(moduleSchema, packageName)
        val reducerGenerator = ReducerGenerator(moduleSchema, packageName)
        val moduleGenerator = ModuleGenerator(moduleSchema, packageName)

        outDir.createDirectories()
        val outFile = outDir.toFile()

        val files = buildList {
            // Custom types
            addAll(typeGenerator.generateTypeFiles())

            // Table row types + table handles
            for (table in moduleSchema.publicTables) {
                val productType = moduleSchema.tableProductType(table)
                add(typeGenerator.generateTableRowFile(table.sourceName, productType))
            }
            addAll(tableHandleGenerator.generateTableHandleFiles())
            add(tableHandleGenerator.generateRemoteTablesFile())

            // Reducers
            add(reducerGenerator.generateReducerFile())
            add(reducerGenerator.generateRemoteReducersFile())

            // Module-level wiring
            add(moduleGenerator.generateDeserializerMapFile())
            add(moduleGenerator.generatePkExtractorMapFile())
            add(moduleGenerator.generateBuilderExtensionFile())
        }

        for (file in files) {
            file.writeTo(outFile)
        }

        echo("Generated ${files.size} files in ${outDir.toAbsolutePath()}")
    }
}

public fun main(args: Array<String>) {
    GenerateCommand().main(args)
}
