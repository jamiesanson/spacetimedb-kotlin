package dev.sanson.spacetimedb.gradle

import dev.sanson.spacetimedb.codegen.generator.ModuleGenerator
import dev.sanson.spacetimedb.codegen.generator.ReducerGenerator
import dev.sanson.spacetimedb.codegen.generator.TableHandleGenerator
import dev.sanson.spacetimedb.codegen.generator.TypeGenerator
import dev.sanson.spacetimedb.codegen.schema.ModuleSchema
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that generates typed Kotlin sources from a SpacetimeDB module schema.
 */
@CacheableTask
public abstract class GenerateSpacetimeTypesTask : DefaultTask() {

    /** The V10 module schema JSON file. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val schemaFile: RegularFileProperty

    /** Package name for generated code. */
    @get:Input
    public abstract val packageName: Property<String>

    /** Output directory for generated Kotlin sources. */
    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    @TaskAction
    public fun generate() {
        val schemaJson = schemaFile.get().asFile.readText()
        val schema = ModuleSchema.fromJson(schemaJson)
        val pkg = packageName.get()
        val outDir = outputDirectory.get().asFile

        outDir.deleteRecursively()
        outDir.mkdirs()

        val typeGenerator = TypeGenerator(schema, pkg)
        val tableHandleGenerator = TableHandleGenerator(schema, pkg)
        val reducerGenerator = ReducerGenerator(schema, pkg)
        val moduleGenerator = ModuleGenerator(schema, pkg)

        val files = buildList {
            addAll(typeGenerator.generateTypeFiles())

            for (table in schema.publicTables) {
                val productType = schema.tableProductType(table)
                add(typeGenerator.generateTableRowFile(table.sourceName, productType))
            }

            addAll(tableHandleGenerator.generateTableHandleFiles())
            add(tableHandleGenerator.generateRemoteTablesFile())
            addAll(tableHandleGenerator.generateTableHandleImplFiles())
            add(tableHandleGenerator.generateRemoteTablesImplFile())

            add(reducerGenerator.generateReducerFile())
            add(reducerGenerator.generateRemoteReducersFile())
            add(reducerGenerator.generateRemoteReducersImplFile())

            add(moduleGenerator.generateDeserializerMapFile())
            add(moduleGenerator.generatePkExtractorMapFile())
            add(moduleGenerator.generateBuilderExtensionFile())
            add(moduleGenerator.generateDbConnectionFile())
            add(moduleGenerator.generateDbConnectionBuilderFile())
        }

        for (file in files) {
            file.writeTo(outDir)
        }

        logger.lifecycle("SpacetimeDB: generated ${files.size} files in $outDir")
    }
}
