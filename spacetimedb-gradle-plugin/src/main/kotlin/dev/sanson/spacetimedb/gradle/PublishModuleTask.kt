package dev.sanson.spacetimedb.gradle

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Publishes a SpacetimeDB module to a server by running `spacetime publish`.
 *
 * `spacetime publish` compiles the module from source itself, so this task does not depend on
 * [BuildModuleTask].
 */
public abstract class PublishModuleTask
@Inject
constructor(private val execOperations: ExecOperations) : DefaultTask() {

    /** The SpacetimeDB module project directory. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val modulePath: DirectoryProperty

    /** The database/module name to publish as. */
    @get:Input public abstract val databaseName: Property<String>

    /** When to destroy existing data on publish (`--delete-data`). Omitted when unset. */
    @get:Input @get:Optional public abstract val deleteData: Property<DeleteDataMode>

    /** Run publish non-interactively (`--yes`). */
    @get:Input @get:Optional public abstract val publishNonInteractive: Property<Boolean>

    /** Allow publishing changes that break existing clients (`--break-clients`). */
    @get:Input @get:Optional public abstract val allowBreakingClients: Property<Boolean>

    /**
     * Optional `--server` to publish to (e.g. `"local"` or a URL). Uses the CLI default if unset.
     */
    @get:Input @get:Optional public abstract val server: Property<String>

    /** Extra raw CLI options for `spacetime publish` not covered by the typed options. */
    @get:Input public abstract val publishOptions: ListProperty<String>

    /**
     * Explicit path to the `spacetime` CLI. When unset, the task auto-resolves
     * `~/.local/bin/spacetime` and finally falls back to `spacetime` on `PATH`.
     */
    @get:Input @get:Optional public abstract val spacetimeCli: Property<String>

    @TaskAction
    public fun publish() {
        val moduleDir = modulePath.get().asFile
        val name = databaseName.get()

        logger.lifecycle("SpacetimeDB: publishing '$name' from ${moduleDir.path}")

        val args = mutableListOf(SpacetimeCli.resolve(spacetimeCli.orNull), "publish")
        deleteData.orNull?.let { args += "--delete-data=${it.cliValue}" }
        if (publishNonInteractive.getOrElse(false)) args += "--yes"
        if (allowBreakingClients.getOrElse(false)) args += "--break-clients"
        server.orNull?.takeIf { it.isNotBlank() }?.let { args += listOf("--server", it) }
        args += publishOptions.getOrElse(emptyList())
        args += name

        execOperations
            .exec { spec ->
                spec.workingDir = moduleDir
                spec.commandLine(args)
            }
            .assertNormalExitValue()
    }
}
