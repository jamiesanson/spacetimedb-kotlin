package dev.sanson.spacetimedb.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Builds a SpacetimeDB module and extracts its schema to a JSON file.
 *
 * Runs `spacetime build -p <modulePath>` followed by
 * `spacetimedb-standalone extract-schema <wasm>` to produce the schema JSON.
 *
 * Requires both the `spacetime` CLI and `spacetimedb-standalone` to be available.
 */
public abstract class BuildModuleTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    /** The SpacetimeDB module project directory. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val modulePath: DirectoryProperty

    /** Extra CLI options for `spacetime build`. */
    @get:Input
    public abstract val buildOptions: ListProperty<String>

    /** The output schema JSON file. */
    @get:OutputFile
    public abstract val schemaFile: RegularFileProperty

    @TaskAction
    public fun buildAndExtract() {
        val moduleDir = modulePath.get().asFile

        logger.lifecycle("SpacetimeDB: building module at ${moduleDir.path}")

        // Step 1: Build the module
        val buildArgs = mutableListOf("spacetime", "build", "-p", moduleDir.absolutePath)
        buildArgs.addAll(buildOptions.getOrElse(emptyList()))

        execOperations.exec { spec ->
            spec.commandLine(buildArgs)
        }.assertNormalExitValue()

        // Step 2: Find the .wasm output
        val wasmFile = findWasmOutput(moduleDir)

        logger.lifecycle("SpacetimeDB: extracting schema from ${wasmFile.name}")

        // Step 3: Extract schema via spacetimedb-standalone
        val standalonePath = resolveStandalone()
        val output = schemaFile.get().asFile

        val schemaOutput = ByteArrayOutputStream()
        execOperations.exec { spec ->
            spec.commandLine(standalonePath, "extract-schema", wasmFile.absolutePath)
            spec.standardOutput = schemaOutput
        }.assertNormalExitValue()

        output.writeBytes(schemaOutput.toByteArray())
        logger.lifecycle("SpacetimeDB: schema written to ${output.name}")
    }

    private fun findWasmOutput(moduleDir: File): File {
        // Convention: Rust modules output to target/wasm32-unknown-unknown/release/*.wasm
        val releaseDir = File(moduleDir, "target/wasm32-unknown-unknown/release")
        val debugDir = File(moduleDir, "target/wasm32-unknown-unknown/debug")

        val candidates = sequenceOf(releaseDir, debugDir)
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.listFiles()?.asSequence()?.filter { it.extension == "wasm" } ?: emptySequence()
            }
            // Prefer optimized wasm, then regular wasm, skip deps
            .filter { !it.path.contains("/deps/") }
            .sortedByDescending { it.lastModified() }
            .toList()

        // Prefer .opt.wasm (optimized) if available
        return candidates.firstOrNull { it.name.endsWith(".opt.wasm") }
            ?: candidates.firstOrNull()
            ?: error(
                "No .wasm file found in $releaseDir or $debugDir. " +
                    "Ensure 'spacetime build' completed successfully."
            )
    }

    private fun resolveStandalone(): String {
        // spacetimedb-standalone lives alongside the spacetime CLI version binaries
        val home = System.getProperty("user.home")
        val versionDirs = File("$home/.local/share/spacetime/bin")
            .listFiles()
            ?.filter { it.isDirectory }
            ?.sortedDescending()
            ?: emptyList()

        for (dir in versionDirs) {
            val standalone = File(dir, "spacetimedb-standalone")
            if (standalone.isFile && standalone.canExecute()) {
                return standalone.absolutePath
            }
        }

        // Fallback: maybe it's on PATH
        return "spacetimedb-standalone"
    }
}

