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
| `withCompression(compression: Compression)` | Preferred compression (`Compression.None` or `Compression.Gzip` on JVM) |
| `onConnect(callback)` | Called when connection is established. Receives `(identity: Identity, token: String, connectionId: ConnectionId)`. Save the `token` to reconnect as the same identity later. |
| `onDisconnect(callback)` | Called when connection ends. Receives `(error: SpacetimeError?)` — null for clean disconnect. |
| `onConnectError(callback)` | Called if connection fails. Receives `(error: SpacetimeError)`. |
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
