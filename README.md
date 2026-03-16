# SpacetimeDB Kotlin Multiplatform SDK

A Kotlin Multiplatform client SDK for [SpacetimeDB](https://spacetimedb.com), targeting JVM, JS, and Native platforms.
Provides the same functionality as the [Rust client SDK](https://spacetimedb.com/docs/sdks/rust) with idiomatic Kotlin
APIs (coroutines, `kotlinx.serialization`).

> **Status**: Alpha — API may change between minor versions.

## Quick Start

Add the Gradle plugin:

```kotlin
// build.gradle.kts
plugins {
    id("dev.sanson.spacetimedb") version "<latest>"
}

spacetimedb {
    modulePath.set(file("server"))
    packageName.set("com.example.game")
}
```

Connect to your database:

```kotlin
import com.example.game.DbConnection
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val conn = DbConnection.builder()
        .withUri("http://localhost:3000")
        .withDatabaseName("my-game")
        .onConnect { identity, token, connectionId ->
            println("Connected as $identity")
        }
        .build(this)

    conn.subscriptionBuilder()
        .onApplied { println("Initial data synced") }
        .subscribe("SELECT * FROM player")

    conn.db.player.onInsert { event, player ->
        println("Player joined: ${player.name}")
    }

    conn.reducers.addPlayer("Bob")
}
```

See the [Getting Started](https://jamiesanson.github.io/spacetimedb-kotlin/getting-started/) guide for the full walkthrough.

## Documentation

Full documentation will be published soon.

## Platform Support

| Feature | JVM | JS (Node/Browser) | Native |
|---------|-----|--------------------|--------|
| WebSocket transport | ✅ | ✅ | ✅ |
| Gzip compression | ✅ | ❌ | ❌ |
| Brotli compression | ✅ | ❌ | ❌ |
| Credential persistence | ✅ | Node only¹ | ✅ |

¹ Node.js requires passing an Okio `FileSystem` instance. Browser has no filesystem access.

## License

TBD
