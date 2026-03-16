# Generated Code

The SpacetimeDB Gradle plugin (or standalone codegen CLI) reads your module's schema and generates typed Kotlin bindings. This page describes what gets generated and how to use it.

For plugin configuration, see [Gradle Plugin](gradle-plugin.md). For the full runtime API, see [SDK Reference](sdk-reference.md).

## Overview

Given a SpacetimeDB module, codegen produces the following files in your configured `packageName`:

| Generated             | Description                                                        |
|-----------------------|--------------------------------------------------------------------|
| Row types             | `@Serializable` classes matching each table                        |
| Custom types          | Product types (classes), sum types (sealed classes or enums)       |
| Table handles         | `{Table}TableHandle` interfaces + `RemoteTables`                   |
| Reducer types         | `Reducer` sealed class + `RemoteReducers` interface                |
| `DbConnection`        | Entry point implementing `DbContext<RemoteTables, RemoteReducers>` |
| `DbConnectionBuilder` | Builder with auto-configured deserializers                         |

## Row types

Each table becomes a `@Serializable` class with BSATN-compatible field types:

```kotlin
// From: #[spacetimedb::table(name = player, public)]
@Serializable
public class Player(
    public val id: ULong,
    public val name: String,
    public val score: UInt,
)
```

Field names are converted from `snake_case` to `camelCase`. The original name is preserved via `@SerialName` when they differ.

## Table handles

Each table gets a typed handle interface extending `Table<Row>` or `TableWithPrimaryKey<Row>`. Unique and primary key columns get `findBy` methods:

```kotlin
public interface PlayerTableHandle : TableWithPrimaryKey<Player> {
    public fun findById(id: ULong): Player?
    public fun findByName(name: String): Player?
}
```

All table handles are aggregated into `RemoteTables`:

```kotlin
public interface RemoteTables {
    public val player: PlayerTableHandle
    public val message: MessageTableHandle
}
```

Access via `conn.db`:

```kotlin
val alice = conn.db.player.findByName("Alice")
for (player in conn.db.player) { /* ... */ }
```

## Reducer sealed class

A sealed class encoding which reducer ran, including its arguments:

```kotlin
public sealed class Reducer {
    public data class AddPlayer(public val name: String) : Reducer()
    public data class SetScore(public val playerId: ULong, public val score: UInt) : Reducer()
    public data object Init : Reducer()  // parameterless reducers become data objects
}
```

This type appears in `ReducerEvent.reducer` when observing reducer completions.

## Remote reducers

Typed methods for calling reducers and registering callbacks:

```kotlin
public interface RemoteReducers {
    // Invoke
    public fun addPlayer(name: String)
    public fun setScore(playerId: ULong, score: UInt)

    // Observe completion
    public fun onAddPlayer(callback: (ReducerEvent<Reducer>) -> Unit): CallbackId
    public fun removeOnAddPlayer(id: CallbackId)
    public fun onSetScore(callback: (ReducerEvent<Reducer>) -> Unit): CallbackId
    public fun removeOnSetScore(id: CallbackId)
}
```

Access via `conn.reducers`:

```kotlin
conn.reducers.addPlayer("Bob")
conn.reducers.onAddPlayer { event ->
    println("add_player completed with ${event.status}")
}
```

## DbConnection and DbConnectionBuilder

The generated `DbConnection` implements `DbContext<RemoteTables, RemoteReducers>` and is the entry point for interacting with your module:

```kotlin
public class DbConnection : DbContext<RemoteTables, RemoteReducers> {
    override val db: RemoteTables
    override val reducers: RemoteReducers
    override val identity: Identity?
    override val connectionId: ConnectionId?
    override val isActive: Boolean

    override fun subscriptionBuilder(): SubscriptionBuilder
    override fun disconnect()

    public companion object {
        public fun builder(): DbConnectionBuilder
    }
}
```

The generated `DbConnectionBuilder` wraps `SpacetimeDbConnectionBuilder` and auto-configures table deserializers, so you don't need to register them manually:

```kotlin
val conn = DbConnection.builder()
    .withUri("http://localhost:3000")
    .withDatabaseName("my-game")
    .onConnect { identity, token, connectionId -> /* ... */ }
    .build(scope)
```

## Custom algebraic types

SpacetimeDB algebraic types are mapped to Kotlin types:

**Product types** (structs) → `@Serializable` class:

```kotlin
@Serializable
public class Vec3(public val x: Float, public val y: Float, public val z: Float)
```

**Sum types with only unit variants** → `@Serializable` enum:

```kotlin
@Serializable
public enum class PlayerStatus { Active, Inactive, Banned }
```

**Sum types with data variants** → `@Serializable` sealed class:

```kotlin
@Serializable
public sealed class Shape {
    public data class Circle(val radius: Double) : Shape()
    public data class Rectangle(val width: Double, val height: Double) : Shape()
    public data object None : Shape()
}
```

## Standalone CLI usage

The codegen can be run without the Gradle plugin:

```bash
java -jar spacetimedb-codegen.jar \
  --schema schema.json \
  --out-dir src/generated \
  --package com.example.mymodule
```
