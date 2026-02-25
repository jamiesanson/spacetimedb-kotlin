package dev.sanson.spacetimedb.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * Configuration extension for the SpacetimeDB code generation plugin.
 *
 * Configure either [schemaFile] (pre-extracted JSON) or [wasmModule]
 * (to run `spacetime extract-schema` automatically).
 *
 * ```kotlin
 * spacetimedb {
 *     schemaFile.set(file("schema.json"))
 *     // or
 *     wasmModule.set(file("target/wasm32-unknown-unknown/release/module.wasm"))
 *     packageName.set("com.example.mymodule")
 * }
 * ```
 */
public interface SpacetimeDbExtension {

    /**
     * Path to a pre-extracted V10 module schema JSON file
     * (output of `spacetime extract-schema`).
     *
     * Mutually exclusive with [wasmModule]. If both are set, [schemaFile] takes precedence.
     */
    public val schemaFile: RegularFileProperty

    /**
     * Path to a compiled SpacetimeDB `.wasm` module.
     *
     * When set, the plugin runs `spacetime extract-schema <wasm>` to produce
     * the schema JSON before code generation.
     */
    public val wasmModule: RegularFileProperty

    /**
     * Package name for generated Kotlin sources (e.g. `"com.example.mymodule"`).
     */
    public val packageName: Property<String>
}
