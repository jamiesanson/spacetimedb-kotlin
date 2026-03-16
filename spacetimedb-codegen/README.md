# Module spacetimedb-codegen

Code generator for typed SpacetimeDB Kotlin bindings. Reads a V10 module schema (JSON) and
produces `@Serializable` table row types, table handle classes with callback support, reducer
wrappers, and module-level wiring that connects generated code to the core SDK.

# Package dev.sanson.spacetimedb.codegen

CLI entry point. Run the [GenerateCommand][dev.sanson.spacetimedb.codegen.GenerateCommand] with
a module schema JSON file to produce Kotlin source files.

# Package dev.sanson.spacetimedb.codegen.generator

Code generation implementations for table rows, table handles, reducers, and module wiring.

# Package dev.sanson.spacetimedb.codegen.schema

Schema parsing and type resolution. Deserializes the SpacetimeDB V10 module definition and
resolves algebraic type references into concrete Kotlin type mappings.
