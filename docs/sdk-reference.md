# SDK Reference

Full API reference for the SpacetimeDB Kotlin SDK. For a quick introduction, see the [README](../README.md).

## `DbConnection` type

A connection to a remote SpacetimeDB database. This type is **generated per-module** by the codegen plugin, and provides typed access to your module's tables and reducers.

```kotlin
public class DbConnection : DbContext<RemoteTables, RemoteReducers> {
    override val db: RemoteTables
    override val reducers: RemoteReducers
    override val identity: Identity?
    override val connectionId: ConnectionId?
    override val isActive: Boolean

    fun subscriptionBuilder(): SubscriptionBuilder
    fun disconnect()

    companion object {
        fun builder(): DbConnectionBuilder
    }
}
```

## `DbContext` interface

The core interface implemented by `DbConnection`. Generic over `Tables` and `Reducers`, which are bound to your module's generated types.

```kotlin
public interface DbContext<Tables, Reducers> {
    val db: Tables                      // Access subscribed table rows
    val reducers: Reducers              // Call reducers + register callbacks
    val identity: Identity?             // This client's identity (null before on_connect)
    val connectionId: ConnectionId?     // This connection's ID (null before on_connect)
    val isActive: Boolean               // Whether the WebSocket is connected

    fun subscriptionBuilder(): SubscriptionBuilder
    fun disconnect()
}
```

## Connect to a database

Construct a `DbConnection` by calling `DbConnection.builder()`, chaining configuration methods, then calling `.build(scope)`. You must at least specify `withUri` and `withDatabaseName`.

```kotlin
val conn = DbConnection.builder()
    .withUri("http://localhost:3000")
    .withDatabaseName("my-database")
    .withToken(savedToken)            // optional: authenticate as a returning user
    .withCompression(Compression.None) // optional: default is None
    .onConnect { identity, token, connectionId -> /* ... */ }
    .onDisconnect { error -> /* ... */ }
    .onConnectError { error -> /* ... */ }
    .build(scope)                     // suspending; needs a CoroutineScope
```

### Builder methods

| Method | Description |
|--------|-------------|
| `withUri(uri: String)` | URI of the SpacetimeDB instance (e.g. `"http://localhost:3000"`) |
| `withDatabaseName(name: String)` | Name or identity of the database |
| `withToken(token: String?)` | Auth token from a previous `onConnect` callback. Pass `null` for anonymous. |
| `withCompression(compression: Compression)` | Preferred compression (`Compression.None`, `Compression.Brotli`, or `Compression.Gzip`). Brotli and Gzip are JVM-only. |
| `onConnect(callback)` | Called when connection is established. Receives `(identity: Identity, token: String, connectionId: ConnectionId)`. Save the `token` to reconnect as the same identity later. |
| `onDisconnect(callback)` | Called when connection ends. Receives `(error: SpacetimeError?)` — null for clean disconnect. |
| `onConnectError(callback)` | Called if connection fails. Receives `(error: SpacetimeError)`. |
| `withReconnect(config: ReconnectConfig)` | Enable auto-reconnect with exponential backoff on abnormal disconnects. |
| `build(scope: CoroutineScope)` | Finalize and connect. Suspending function — the connection runs within the given scope. |

## Subscribe to queries

After connecting, subscribe to SQL queries to receive rows in your client cache. Rows matching your queries are automatically synced and kept up-to-date.

```kotlin
val handle = conn.subscriptionBuilder()
    .onApplied { println("Initial rows loaded") }
    .onError { error -> println("Subscription failed: $error") }
    .subscribe("SELECT * FROM player", "SELECT * FROM message")
```

### `SubscriptionBuilder`

| Method | Description |
|--------|-------------|
| `onApplied(callback: () -> Unit)` | Called when the subscription is applied and initial rows are in the cache |
| `onError(callback: (String) -> Unit)` | Called if the subscription is rejected (e.g. invalid SQL) |
| `subscribe(vararg queries: String): SubscriptionHandle` | Subscribe to one or more SQL queries |
| `subscribe(queries: List<String>): SubscriptionHandle` | Subscribe to a list of SQL queries |

See the [SpacetimeDB SQL Reference](https://spacetimedb.com/docs/reference/sql#subscriptions) for supported subscription queries.

### `SubscriptionHandle`

| Member | Description |
|--------|-------------|
| `isActive: Boolean` | `true` if the subscription has been applied and rows are in the cache |
| `isEnded: Boolean` | `true` if the subscription has ended (unsubscribed or errored) |
| `unsubscribe(onEnded: (() -> Unit)? = null)` | Terminate this subscription. Matching rows are removed from the cache, triggering `onDelete` callbacks. Optional `onEnded` callback fires when removal is complete. |

## Access the client cache

The `conn.db` field provides typed access to subscribed table rows. Each table in your module has a property on `RemoteTables` (generated), which returns a table handle.

```kotlin
// Iterate all subscribed rows
for (player in conn.db.player) {
    println("${player.name}: ${player.score}")
}

// Count
val total = conn.db.player.count

// Find by unique/primary key column
val alice = conn.db.player.findByName("Alice")
val player = conn.db.player.findById(42u)
```

### `Table<Row>` interface

All generated table handles implement `Table<Row>` (and `Iterable<Row>`):

| Member | Description |
|--------|-------------|
| `count: Int` | Number of subscribed rows in the cache |
| `iterator(): Iterator<Row>` | Iterate all cached rows |
| `onInsert(callback: (Event<*>, Row) -> Unit): CallbackId` | Register a callback for row insertions |
| `removeOnInsert(id: CallbackId)` | Deregister an insert callback |
| `onDelete(callback: (Event<*>, Row) -> Unit): CallbackId` | Register a callback for row deletions |
| `removeOnDelete(id: CallbackId)` | Deregister a delete callback |

### `TableWithPrimaryKey<Row>` interface

Tables with a `#[primary_key]` column also implement:

| Member | Description |
|--------|-------------|
| `onUpdate(callback: (Event<*>, oldRow: Row, newRow: Row) -> Unit): CallbackId` | Register a callback for row updates (same primary key, different data) |
| `removeOnUpdate(id: CallbackId)` | Deregister an update callback |

### Row callback events

Row callbacks receive an `Event<*>` as their first argument, which describes _why_ the row changed:

| Event variant | Description |
|---------------|-------------|
| `Event.Reducer(event: ReducerEvent)` | Change caused by a reducer we called |
| `Event.Transaction` | Change caused by another client's transaction |
| `Event.SubscribeApplied` | Row arrived as part of initial subscription sync |
| `Event.UnsubscribeApplied` | Row removed due to unsubscription |
| `Event.SubscribeError(error)` | Row removed due to subscription error |
| `Event.Disconnected` | Connection lost |

```kotlin
conn.db.player.onInsert { event, player ->
    when (event) {
        is Event.Reducer -> println("We caused this: ${event.event.reducer}")
        is Event.SubscribeApplied -> println("Initial sync: ${player.name}")
        is Event.Transaction -> println("Someone else added: ${player.name}")
        else -> {}
    }
}
```

## Observe and invoke reducers

The `conn.reducers` field provides typed methods for calling reducers and registering callbacks.

Each reducer defined by your module generates three methods on `RemoteReducers`:

| Method pattern | Description |
|----------------|-------------|
| `reducerName(args...)` | Call the reducer on the server |
| `onReducerName(callback: (ReducerEvent<Reducer>) -> Unit): CallbackId` | Register a callback for when this reducer completes (committed, failed, or panicked) |
| `removeOnReducerName(id: CallbackId)` | Deregister a reducer callback |

```kotlin
// Call a reducer
conn.reducers.addPlayer("Alice")

// Observe when the reducer completes
val callbackId = conn.reducers.onAddPlayer { event ->
    println("add_player ${event.status} at ${event.timestamp}")
}

// Later, remove the callback
conn.reducers.removeOnAddPlayer(callbackId)
```

### `ReducerEvent<R>`

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | `Timestamp` | When the reducer was invoked on the server |
| `status` | `Status` | Whether the reducer committed, failed, or panicked |
| `reducer` | `R` | The `Reducer` enum variant with the reducer's arguments |

### `Status`

| Variant | Description |
|---------|-------------|
| `Status.Committed` | The reducer ran successfully and its changes were committed |
| `Status.Failed(message: String)` | The reducer returned an error. Changes were rolled back. |
| `Status.Panic(message: String)` | The reducer panicked or hit an internal error. Changes were rolled back. |

Reducer callbacks fire for **all outcomes** — committed, failed, and panicked — matching the behavior of the Rust SDK. Row callbacks (`onInsert`, `onDelete`, `onUpdate`) only fire for committed reducers that actually changed rows.

## Identify a client

### `Identity`

```kotlin
@JvmInline value class Identity(val bytes: U256)
```

A unique public identifier for a client connected to a database. Received in the `onConnect` callback. Stable across reconnections when using the same auth token.

### `ConnectionId`

```kotlin
@JvmInline value class ConnectionId(val bytes: U128)
```

An opaque identifier for a specific connection session, distinguishing multiple connections from the same `Identity`.

### `CallbackId`

```kotlin
@JvmInline value class CallbackId(val id: Long)
```

Opaque handle returned by all callback registration methods (`onInsert`, `onUpdate`, `onDelete`, `on<Reducer>`). Pass to the corresponding `remove*` method to deregister the callback.

## One-off queries

Use `remoteQuery()` to execute a single-shot SQL query and get results back directly, without creating a persistent subscription. Useful for ad-hoc queries, dashboards, and data exploration.

```kotlin
// Reified — serializer inferred from type parameter
val topPlayers: List<Player> = conn.remoteQuery("SELECT * FROM player WHERE score > 100")

// Explicit serializer
val players = conn.remoteQuery("SELECT * FROM player", Player.serializer())
```

`remoteQuery` is a suspend function — it sends the query to the server and suspends until the result arrives. If the server rejects the query, a `SpacetimeError.QueryError` is thrown.

| Overload | Description |
|----------|-------------|
| `suspend fun <T> remoteQuery(sql: String, serializer: KSerializer<T>): List<T>` | Query with an explicit serializer |
| `suspend inline fun <reified T> remoteQuery(sql: String): List<T>` | Query with an inferred serializer |

## Procedure calls

Procedures are non-transactional server functions that return values directly, unlike reducers which produce side effects via subscription updates.

```kotlin
// Reified — serializer inferred from type parameter
val count: UInt = conn.callProcedure("get_count", byteArrayOf())

// Explicit serializer
val count = conn.callProcedure("get_count", byteArrayOf(), UInt.serializer())

// Low-level — returns ProcedureOutcome with raw bytes
val outcome = conn.callProcedure("get_count", byteArrayOf())
when (outcome) {
    is ProcedureOutcome.Returned -> println("Result: ${outcome.value.size} bytes")
    is ProcedureOutcome.InternalError -> println("Error: ${outcome.message}")
}
```

| Overload | Description |
|----------|-------------|
| `suspend fun callProcedure(name: String, args: ByteArray): ProcedureOutcome` | Low-level call returning raw outcome |
| `suspend fun <T> callProcedure(name: String, args: ByteArray, serializer: KSerializer<T>): T` | Deserializes the return value; throws `SpacetimeError.Internal` on error |
| `suspend inline fun <reified T> callProcedure(name: String, args: ByteArray): T` | Reified convenience overload |

### `ProcedureOutcome`

| Variant | Fields | Description |
|---------|--------|-------------|
| `ProcedureOutcome.Returned` | `value: ByteArray`, `timestamp`, `executionDuration` | The procedure returned a value successfully |
| `ProcedureOutcome.InternalError` | `message: String`, `timestamp`, `executionDuration` | The procedure failed with a server-side error |

## Flow extensions

The SDK provides `Flow`-based extensions for observing table state reactively. These are cold flows — they begin observing when collected and clean up when the collector is cancelled.

### Per-event flows

Observe individual table events as they happen:

```kotlin
conn.db.player.insertFlow()
    .collect { (event, player) -> println("Inserted: ${player.name}") }

conn.db.player.deleteFlow()
    .collect { (event, player) -> println("Deleted: ${player.name}") }

// Only available on tables with a primary key
conn.db.player.updateFlow()
    .collect { (event, old, new) -> println("${new.name}: ${old.score} → ${new.score}") }
```

### `rowsFlow()` — live table snapshots

Emits the complete list of rows whenever the table changes (insert, delete, or update). Ideal for driving UI state.

```kotlin
conn.db.player.rowsFlow()
    .collect { players ->
        println("${players.size} players online")
        updatePlayerList(players)
    }
```

Results are **conflated** — if multiple changes arrive faster than the collector processes them, intermediate snapshots are dropped and only the latest state is delivered.

| Extension | Available on | Description |
|-----------|-------------|-------------|
| `Table<Row>.rowsFlow(): Flow<List<Row>>` | All tables | Emits on insert/delete |
| `TableWithPrimaryKey<Row>.rowsFlow(): Flow<List<Row>>` | Primary key tables | Emits on insert/delete/update |

## Auto-reconnect

Configure automatic reconnection with exponential backoff when the WebSocket connection drops unexpectedly (network change, server restart, etc.). User-initiated disconnects via `disconnect()` are never retried.

```kotlin
val conn = DbConnection.builder()
    .withUri("wss://my-server.com")
    .withDatabaseName("my-db")
    .withReconnect(ReconnectConfig(
        maxAttempts = 10,
        initialDelay = 1.seconds,
        maxDelay = 30.seconds,
    ))
    .onConnect { identity, token, connectionId ->
        println("Connected as $identity")
    }
    .onDisconnect { error ->
        if (error != null) println("Disconnected permanently: $error")
    }
    .build(scope)
```

On reconnect, the SDK automatically re-subscribes to all active subscriptions and re-authenticates with the existing token. The client cache is cleared and repopulated from the fresh subscription snapshot.

### `ReconnectConfig`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `maxAttempts` | `Int` | `5` | Maximum number of reconnection attempts before giving up |
| `initialDelay` | `Duration` | `1.seconds` | Delay before the first reconnection attempt |
| `maxDelay` | `Duration` | `30.seconds` | Maximum delay between attempts (caps exponential growth) |

If all attempts are exhausted, `onDisconnect` is called with a `SpacetimeError`.

## DSL builder

As an alternative to the method-chaining builder, a Kotlin DSL is available:

```kotlin
val conn = SpacetimeDbConnection(scope) {
    uri = "http://localhost:3000"
    databaseName = "my-db"
    token = savedToken
    compression = Compression.Brotli
    reconnect = ReconnectConfig(maxAttempts = 10)
    onConnect { identity, token, connectionId ->
        println("Connected as $identity")
    }
    onDisconnect { error ->
        println("Disconnected: $error")
    }
}
```

## Errors

All SDK errors extend `SpacetimeError`:

| Variant | Description |
|---------|-------------|
| `SpacetimeError.Disconnected` | The connection is already disconnected |
| `SpacetimeError.FailedToConnect` | Failed to establish a WebSocket connection |
| `SpacetimeError.SubscriptionError` | The server rejected a subscription query |
| `SpacetimeError.QueryError` | The server rejected a one-off query (`remoteQuery`) |
| `SpacetimeError.AlreadyEnded` | The subscription has already ended |
| `SpacetimeError.AlreadyUnsubscribed` | Unsubscribe was already called |
| `SpacetimeError.Internal` | An internal or unexpected SDK error |
