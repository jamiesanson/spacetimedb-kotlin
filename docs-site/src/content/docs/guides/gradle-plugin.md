---
title: Gradle Plugin
description: Configure the dev.sanson.spacetimedb Gradle plugin for build and codegen.
---


The `dev.sanson.spacetimedb` Gradle plugin wraps the SpacetimeDB build and codegen into your Gradle build pipeline.

## Setup

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal() // during pre-release
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    id("dev.sanson.spacetimedb") version "0.1.0"
}

spacetimedb {
    modulePath.set(file("server"))          // path to your SpacetimeDB Rust module
    packageName.set("com.example.game")     // package for generated Kotlin sources
}
```

## Configuration

| Property | Type | Description |
|----------|------|-------------|
| `modulePath` | `DirectoryProperty` | Path to your SpacetimeDB module project (where `Cargo.toml` lives) |
| `packageName` | `Property<String>` | Target package for generated Kotlin sources |
| `buildOptions` | `ListProperty<String>` | Extra CLI flags for `spacetime build` (e.g. `--debug`) |

## Tasks

| Task | Description |
|------|-------------|
| `buildSpacetimeModule` | Runs `spacetime build` → `spacetimedb-standalone extract-schema` |
| `generateSpacetimeTypes` | Generates Kotlin sources from the extracted schema |

Generated sources are automatically wired into Kotlin JVM or KMP `commonMain` source sets. The `spacetimedb-core` runtime dependency is added automatically.

## Prerequisites

The `spacetime` CLI must be installed and on your PATH. Install from [spacetimedb.com](https://spacetimedb.com).

## Using as an included build

For monorepos or SDK development, use Gradle's `includeBuild` to resolve the plugin and library from source:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("path/to/spacetimedb-kotlin") // resolves the plugin
    repositories { gradlePluginPortal(); mavenCentral() }
}

// Also needed for library dependency resolution
includeBuild("path/to/spacetimedb-kotlin")
```

Both `includeBuild` declarations are required:
- The one inside `pluginManagement` resolves the plugin ID `dev.sanson.spacetimedb`
- The top-level one resolves the `spacetimedb-core` library dependency

See the [`example/`](https://github.com/nicksanson/spacetimedb-kotlin/tree/main/example) directory for a working project using this pattern.

## KMP projects

For Kotlin Multiplatform projects, the plugin wires generated sources into `commonMain`. Your `build.gradle.kts` should apply the KMP plugin:

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("dev.sanson.spacetimedb")
}

spacetimedb {
    modulePath.set(file("server"))
    packageName.set("com.example.game")
}
```

The generated code uses only `commonMain`-compatible APIs, so it works across all targets (JVM, JS, Native).
