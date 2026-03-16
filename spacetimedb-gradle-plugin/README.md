# Module spacetimedb-gradle-plugin

Gradle plugin that automates the SpacetimeDB code generation workflow. Builds a SpacetimeDB Rust
module, extracts its schema, and generates typed Kotlin sources — all wired into the Gradle
compilation pipeline.

Supports both JVM and Kotlin Multiplatform projects.

# Package dev.sanson.spacetimedb.gradle

Plugin implementation, extension DSL, and Gradle tasks for building SpacetimeDB modules and
generating typed Kotlin bindings.
