# SpacetimeDB Kotlin Multiplatform SDK

A Kotlin Multiplatform client SDK for [SpacetimeDB](https://spacetimedb.com), targeting JVM, JS, and Native platforms.

> **Status**: Work in progress — not yet ready for production use.

## Quick Start

### 1. Add the Gradle plugin

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal() // for 0.1.0 snapshot
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
    modulePath.set(file("server"))               // path to your SpacetimeDB module project
    packageName.set("com.example.mymodule")      // package for generated Kotlin code
    // buildOptions.set(listOf("--debug"))        // optional: extra flags for `spacetime build`
}

dependencies {
    implementation("dev.sanson.spacetimedb:spacetimedb-core-jvm:0.1.0")
}
```

The plugin will:
1. Run `spacetime build` to compile your module to `.wasm`
2. Extract the schema via `spacetimedb-standalone extract-schema`
3. Generate typed Kotlin sources (table handles, reducers, serialization) into `build/generated/spacetimedb/kotlin/`

### 2. Connect and subscribe

```kotlin
val connection = DbConnection.builder()
    .withUri("ws://localhost:3000")
    .withDatabase("my-database")
    .withModuleDeserializers()  // generated extension — registers table deserializers
    .onConnect { conn, identity, connectionId ->
        println("Connected as $identity")
        conn.subscriptionBuilder()
            .subscribe("SELECT * FROM player")
    }
    .build()
```

### 3. Use generated types

The codegen produces:

- **Row types** — `@Serializable` data classes for each table (e.g. `Player`, `Person`)
- **Table handles** — `PlayerTableHandle`, `RemoteTables` with typed queries (`findByPlayerId()`)
- **Reducer wrappers** — `Reducer` sealed class + `RemoteReducers` interface with typed invoke methods
- **Module wiring** — `withModuleDeserializers()` extension on `DbConnectionBuilder`

## Modules

| Module | Coordinates | Description |
|--------|-------------|-------------|
| `spacetimedb-bsatn` | `dev.sanson.spacetimedb:spacetimedb-bsatn` | BSATN binary serialization format (kotlinx.serialization `BinaryFormat`) |
| `spacetimedb-core` | `dev.sanson.spacetimedb:spacetimedb-core` | Core SDK: connection, client cache, protocol, transport, credentials |
| `spacetimedb-codegen` | `dev.sanson.spacetimedb:spacetimedb-codegen` | Code generator CLI and library (schema → Kotlin sources via KotlinPoet) |
| `spacetimedb-gradle-plugin` | Plugin ID: `dev.sanson.spacetimedb` | Gradle plugin wrapping build + extract-schema + codegen |

### spacetimedb-bsatn

BSATN (Binary SpacetimeDB Algebraic Type Notation) serialization, implementing `kotlinx.serialization.BinaryFormat`.

```kotlin
@Serializable
data class Player(val id: ULong, val name: String)

val bytes = Bsatn.encodeToByteArray(Player(1u, "Alice"))
val player = Bsatn.decodeFromByteArray<Player>(bytes)
```

Key types: `Bsatn`, `BsatnEncoder`, `BsatnDecoder`

### spacetimedb-core

Client-side connection management, caching, and protocol handling.

**Connection lifecycle:**
- `DbConnectionBuilder` — configure URI, database, auth token, compression, callbacks
- `DbConnection` — active connection; call reducers, subscribe to tables
- `SubscriptionBuilder` / `SubscriptionHandle` — manage SQL subscriptions

**Identity & auth:**
- `Identity` — 256-bit user identity (`@JvmInline value class`)
- `ConnectionId` — server-assigned connection ID
- `CredentialFile` — persist auth tokens to `~/.spacetimedb_client_credentials/`

**Table cache:**
- `Table<Row>` — read-only cached table with insert/delete callbacks
- `TableWithPrimaryKey<Row>` — adds update callbacks and `UniqueIndex` lookups
- `ClientCache` — manages all table caches for a connection

**Events:**
- `Event<R>` — hierarchy: `TransactionUpdate`, `SubscribeApplied`, `UnsubscribeApplied`
- `ReducerEvent<R>` — reducer-specific event with `Status` (committed/failed/out-of-energy)
- `TableUpdate<Row>` / `TableAppliedDiff<Row>` — row-level change sets

### spacetimedb-codegen

Generates typed Kotlin client bindings from a SpacetimeDB module schema (`RawModuleDefV10` JSON).

**Generators:**
| Generator | Output |
|-----------|--------|
| `TypeGenerator` | `@Serializable` row classes + custom algebraic types (enums, sum types) |
| `TableHandleGenerator` | `{Table}TableHandle` interfaces, `findByX()` for unique columns, `RemoteTables` |
| `ReducerGenerator` | `Reducer` sealed class (one subtype per client-callable reducer), `RemoteReducers` |
| `ModuleGenerator` | `tableDeserializerMap()` + `DbConnectionBuilder.withModuleDeserializers()` extension |

**CLI usage** (standalone, without Gradle plugin):
```bash
java -jar spacetimedb-codegen.jar \
  --schema schema.json \
  --out-dir src/generated \
  --package com.example.mymodule
```

### spacetimedb-gradle-plugin

Wraps the codegen into a Gradle build pipeline.

**Extension properties:**

| Property | Type | Description |
|----------|------|-------------|
| `modulePath` | `DirectoryProperty` | Path to SpacetimeDB module project (runs `spacetime build`) |
| `packageName` | `Property<String>` | Target package for generated Kotlin sources |
| `buildOptions` | `ListProperty<String>` | Extra CLI flags for `spacetime build` (e.g. `--debug`) |

**Registered tasks:**

| Task | Description |
|------|-------------|
| `buildSpacetimeModule` | Runs `spacetime build`, then `spacetimedb-standalone extract-schema` |
| `generateSpacetimeTypes` | Generates Kotlin sources from the extracted schema |

Generated sources are automatically wired into Kotlin JVM and KMP `commonMain` source sets.

**Prerequisites:** The `spacetime` CLI must be installed and on your PATH. Install from [spacetimedb.com](https://spacetimedb.com).

## Platform Support

### Transport & Compression

The SDK communicates with SpacetimeDB over WebSocket using the v2 BSATN protocol. Server messages may be compressed; the client specifies its preferred compression when connecting.

| Feature | JVM | JS (Node.js) | JS (Browser) | Native |
|---------|-----|--------------|--------------|--------|
| WebSocket transport | ✅ | ✅ | ✅ | ✅ |
| Compression: None | ✅ | ✅ | ✅ | ✅ |
| Compression: Gzip | ✅ | ❌ | ❌ | ❌ |
| Compression: Brotli | ❌ | ❌ | ❌ | ❌ |

> **Note**: Brotli is the default compression in the Rust SDK. JVM Brotli support is planned. On platforms without compression support, use `Compression.None` when connecting.

### Credential Persistence

The SDK can persist authentication tokens to disk at `~/.spacetimedb_client_credentials/`.

| Feature | JVM | JS (Node.js) | JS (Browser) | Native |
|---------|-----|--------------|--------------|--------|
| `CredentialFile.create()` | ✅ | ❌ | ❌ | ✅ |
| `CredentialFile(key, fs, dir)` | ✅ | ✅¹ | ❌ | ✅ |

¹ Node.js users can pass an Okio `FileSystem` instance directly to the `CredentialFile` constructor.

Browser JS has no filesystem access. On Node.js, the convenience `CredentialFile.create()` factory is unavailable because `okio-nodefilesystem` breaks browser compilation; pass your own `FileSystem` instead.

## Dependencies

- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) — Serialization framework (with custom BSATN format)
- [Ktor](https://ktor.io/) — WebSocket client
- [Okio](https://square.github.io/okio/) — Cross-platform file I/O
- [KotlinPoet](https://square.github.io/kotlinpoet/) — Kotlin source generation (codegen only)
- [Clikt](https://ajalt.github.io/clikt/) — CLI argument parsing (codegen only)

## Building

```bash
./gradlew check                          # full build + tests (all modules, all targets)
./gradlew :spacetimedb-codegen:test      # codegen tests only
./gradlew :spacetimedb-gradle-plugin:test # plugin tests only
./gradlew publishToMavenLocal            # publish all artifacts to ~/.m2/repository
```

## Architecture

```
┌─────────────────────┐     ┌──────────────────────┐
│  spacetimedb-bsatn  │     │  spacetimedb-codegen  │
│  (serialization)    │     │  (code generation)    │
└────────┬────────────┘     └──────────┬────────────┘
         │                             │
         ▼                             ▼
┌─────────────────────┐     ┌──────────────────────────┐
│  spacetimedb-core   │     │  spacetimedb-gradle-plugin│
│  (connection, cache)│     │  (build + generate)       │
└─────────────────────┘     └──────────────────────────┘
         ▲                             │
         │                             │
         └─────── generated code ──────┘
```

## License

TBD
