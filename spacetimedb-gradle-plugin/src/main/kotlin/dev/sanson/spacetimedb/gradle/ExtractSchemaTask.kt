package dev.sanson.spacetimedb.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Runs `spacetime extract-schema` on a `.wasm` module to produce a V10 schema JSON file.
 *
 * Requires the `spacetime` CLI to be on the system PATH.
 */
@CacheableTask
public abstract class ExtractSchemaTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    /** The compiled SpacetimeDB `.wasm` module. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val wasmModule: RegularFileProperty

    /** The output schema JSON file. */
    @get:OutputFile
    public abstract val schemaFile: RegularFileProperty

    @TaskAction
    public fun extract() {
        val wasm = wasmModule.get().asFile
        val output = schemaFile.get().asFile

        logger.lifecycle("SpacetimeDB: extracting schema from ${wasm.name}")

        val result = execOperations.exec { spec ->
            spec.commandLine("spacetime", "extract-schema", wasm.absolutePath)
            spec.standardOutput = output.outputStream()
        }

        result.assertNormalExitValue()
        logger.lifecycle("SpacetimeDB: schema written to ${output.name}")
    }
}
