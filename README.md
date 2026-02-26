# SpacetimeDB Kotlin Multiplatform SDK

A Kotlin Multiplatform client SDK for [SpacetimeDB](https://spacetimedb.com), targeting JVM, JS, and Native platforms.

> **Status**: Work in progress — not yet ready for production use.

## Quick Start

Given a SpacetimeDB module with a `player` table and some reducers:

```rust
#[spacetimedb::table(name = player, public)]
pub struct Player {
    #[primary_key]
    #[auto_inc]
    pub id: u64,
    #[unique]
    pub name: String,
    pub score: u32,
}

#[spacetimedb::reducer]
pub fn add_player(ctx: &ReducerContext, name: String) { /* ... */ }

#[spacetimedb::reducer]
pub fn set_score(ctx: &ReducerContext, player_id: u64, score: u32) { /* ... */ }
```

### 1. Add the Gradle plugin

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
    modulePath.set(file("server"))          // your SpacetimeDB module project
    packageName.set("com.example.game")     // package for generated code
}
```

The plugin builds your module, extracts the schema, and generates typed Kotlin bindings into `build/generated/spacetimedb/kotlin/`. The `spacetimedb-core` runtime dependency is added automatically.

### 2. What gets generated

From the module above, the codegen produces:

**Row types** — `@Serializable` classes matching your tables:
```kotlin
// Player.kt (generated)
@Serializable
public class Player(
    public val id: ULong,
    public val name: String,
    public val score: UInt,
)
```

**Table handles** — typed access to the client cache, with `findBy` methods for unique columns:
```kotlin
// PlayerTableHandle.kt (generated)
public interface PlayerTableHandle : TableWithPrimaryKey<Player> {
    public fun findById(id: ULong): Player?
    public fun findByName(name: String): Player?
}

// RemoteTables.kt (generated)
public interface RemoteTables {
    public val player: PlayerTableHandle
}
```

**Reducer wrappers** — typed methods for calling server-side reducers:
```kotlin
// RemoteReducers.kt (generated)
public interface RemoteReducers {
    public fun addPlayer(name: String)
    public fun setScore(playerId: ULong, score: UInt)
}
```

**Module wiring** — a `withModuleDeserializers()` extension that registers all table serializers with the connection builder.

### 3. Connect and use

```kotlin
val connection = DbConnection.builder()
    .withUri("http://localhost:3000")
    .withDatabaseName("my-game")
    .withModuleDeserializers()  // generated — registers table serializers
    .onConnect { identity, token, connectionId ->
        println("Connected as $identity")
    }
    .build(scope)

// Subscribe to tables
connection.subscriptionBuilder()
    .subscribe("SELECT * FROM player")
```

## Modules

| Module | Coordinates | Description |
|--------|-------------|-------------|
| `spacetimedb-bsatn` | `dev.sanson.spacetimedb:spacetimedb-bsatn` | BSATN binary serialization (`kotlinx.serialization` format) |
| `spacetimedb-core` | `dev.sanson.spacetimedb:spacetimedb-core` | Connection, client cache, subscriptions, protocol |
| `spacetimedb-codegen` | `dev.sanson.spacetimedb:spacetimedb-codegen` | Schema → Kotlin source generation (KotlinPoet) |
| `spacetimedb-gradle-plugin` | Plugin ID: `dev.sanson.spacetimedb` | Gradle plugin wrapping build + codegen |

### spacetimedb-bsatn

BSATN (Binary SpacetimeDB Algebraic Type Notation) serialization, implementing `kotlinx.serialization.BinaryFormat`.

```kotlin
@Serializable
data class Player(val id: ULong, val name: String)

val bytes = Bsatn.encodeToByteArray(Player(1u, "Alice"))
val player = Bsatn.decodeFromByteArray<Player>(bytes)
```

### spacetimedb-core

Client-side connection management, caching, and protocol handling.

| Area | Types |
|------|-------|
| Connection | `DbConnectionBuilder`, `DbConnection` |
| Subscriptions | `SubscriptionBuilder`, `SubscriptionHandle` |
| Identity & auth | `Identity`, `ConnectionId`, `CredentialFile` |
| Table cache | `Table<Row>`, `TableWithPrimaryKey<Row>`, `ClientCache` |
| Events | `Event<R>`, `ReducerEvent<R>`, `TableUpdate<Row>`, `TableAppliedDiff<Row>` |

### spacetimedb-codegen

Generates typed Kotlin client bindings from a SpacetimeDB module schema.

| Generator | Output |
|-----------|--------|
| `TypeGenerator` | `@Serializable` row classes + custom algebraic types |
| `TableHandleGenerator` | `{Table}TableHandle` interfaces, `findByX()` for unique columns, `RemoteTables` |
| `ReducerGenerator` | `Reducer` sealed class + `RemoteReducers` interface |
| `ModuleGenerator` | `tableDeserializerMap()` + `withModuleDeserializers()` builder extension |

**Standalone CLI usage** (without the Gradle plugin):
```bash
java -jar spacetimedb-codegen.jar \
  --schema schema.json \
  --out-dir src/generated \
  --package com.example.mymodule
```

### spacetimedb-gradle-plugin

Wraps the codegen into a Gradle build pipeline.

| Property | Type | Description |
|----------|------|-------------|
| `modulePath` | `DirectoryProperty` | Path to your SpacetimeDB module project |
| `packageName` | `Property<String>` | Target package for generated Kotlin sources |
| `buildOptions` | `ListProperty<String>` | Extra CLI flags for `spacetime build` (e.g. `--debug`) |

| Task | Description |
|------|-------------|
| `buildSpacetimeModule` | Runs `spacetime build` → `spacetimedb-standalone extract-schema` |
| `generateSpacetimeTypes` | Generates Kotlin sources from the extracted schema |

Generated sources are automatically wired into Kotlin JVM or KMP `commonMain` source sets. The `spacetimedb-core` dependency is added automatically.

**Prerequisite:** The `spacetime` CLI must be installed and on your PATH. Install from [spacetimedb.com](https://spacetimedb.com).

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
