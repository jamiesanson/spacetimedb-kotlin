---
title: Getting Started
description: Quick start guide for the SpacetimeDB Kotlin Multiplatform SDK.
---
## Quick Start

Given a SpacetimeDB module with tables and reducers:

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
    id("dev.sanson.spacetimedb") version "<latest>"
}

spacetimedb {
    modulePath.set(file("server"))          // your SpacetimeDB module project
    packageName.set("com.example.game")     // package for generated code
}
```

The plugin builds your module, extracts the schema, and generates typed Kotlin bindings. See the [Gradle Plugin](/spacetimedb-kotlin/guides/gradle-plugin) docs for full configuration.

### 2. Connect and use

```kotlin
import com.example.game.DbConnection
import com.example.game.Player
import dev.sanson.spacetimedb.Status
import dev.sanson.spacetimedb.rowsFlow
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val conn = DbConnection.builder()
        .withUri("http://localhost:3000")
        .withDatabaseName("my-game")
        .onConnect { identity, token, connectionId ->
            println("Connected as $identity")
        }
        .build(this)

    // Subscribe to tables — rows sync into the local client cache
    conn.subscriptionBuilder()
        .onApplied { println("Initial data synced") }
        .subscribe("SELECT * FROM player")

    // Query the client cache (local, instant, no network)
    val alice = conn.db.player.findByName("Alice")

    // Call reducers (sends request to server)
    conn.reducers.addPlayer("Bob")

    // Observe table changes in real-time
    conn.db.player.onInsert { event, player ->
        println("Player joined: ${player.name}")
    }

    conn.db.player.onUpdate { event, oldPlayer, newPlayer ->
        println("${newPlayer.name} score: ${oldPlayer.score} → ${newPlayer.score}")
    }

    // Observe table state as a Flow
    conn.db.player.rowsFlow()
        .collect { players -> println("${players.size} players online") }

    // Observe reducer completions
    conn.reducers.onAddPlayer { reducerEvent ->
        when (reducerEvent.status) {
            is Status.Committed -> println("add_player succeeded")
            is Status.Failed -> println("add_player failed: ${reducerEvent.status.message}")
            is Status.Panic -> println("add_player panicked: ${reducerEvent.status.message}")
        }
    }

    conn.disconnect()
}
```

