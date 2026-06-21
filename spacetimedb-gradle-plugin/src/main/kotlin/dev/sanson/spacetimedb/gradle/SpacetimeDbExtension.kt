package dev.sanson.spacetimedb.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Configuration extension for the SpacetimeDB code generation plugin.
 *
 * Point [modulePath] at your SpacetimeDB module project directory. The plugin will build the
 * module, extract the schema, and generate typed Kotlin sources.
 *
 * ```kotlin
 * spacetimedb {
 *     modulePath.set(file("server"))
 *     packageName.set("com.example.mymodule")
 * }
 * ```
 */
public interface SpacetimeDbExtension {

    /**
     * Path to the SpacetimeDB module project directory.
     *
     * The plugin runs `spacetime build -p <dir>` to compile the module, then extracts the schema
     * and generates Kotlin sources.
     */
    public val modulePath: DirectoryProperty

    /** Package name for generated Kotlin sources (e.g. `"com.example.mymodule"`). */
    public val packageName: Property<String>

    /** Extra CLI options passed to `spacetime build` (e.g. `listOf("--debug")`). */
    public val buildOptions: ListProperty<String>

    /**
     * Path to the `spacetime` CLI executable.
     *
     * Useful when the Gradle daemon's `PATH` doesn't include the CLI — the installer only adds
     * `~/.local/bin` to interactive shells (`.zshrc`), so builds launched from IDEs or reused
     * daemons may fail with "A problem occurred starting process 'command 'spacetime''".
     *
     * If unset, the plugin looks for `~/.local/bin/spacetime` and finally falls back to `spacetime`
     * on `PATH`.
     */
    public val spacetimePath: Property<String>

    /** Database/module name used by the `publishSpacetimeModule` task (e.g. `"my_game"`). */
    public val databaseName: Property<String>

    /**
     * When to destroy existing data on publish (`--delete-data`). Omitted when unset.
     *
     * @see DeleteDataMode
     */
    public val deleteData: Property<DeleteDataMode>

    /** Run publish non-interactively, answering "yes" to prompts (`--yes`). Defaults to `false`. */
    public val publishNonInteractive: Property<Boolean>

    /**
     * Allow publishing schema changes that break existing clients (`--break-clients`). Defaults to
     * `false`.
     */
    public val allowBreakingClients: Property<Boolean>

    /**
     * Optional `--server` target for `publishSpacetimeModule` (e.g. `"local"` or a URL). Uses the
     * CLI's default server when unset.
     */
    public val server: Property<String>

    /**
     * Escape hatch for additional raw `spacetime publish` arguments not covered by the typed
     * options above.
     */
    public val publishOptions: ListProperty<String>
}
