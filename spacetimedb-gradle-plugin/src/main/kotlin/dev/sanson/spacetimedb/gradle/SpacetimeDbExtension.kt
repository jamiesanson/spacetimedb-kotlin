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
}
