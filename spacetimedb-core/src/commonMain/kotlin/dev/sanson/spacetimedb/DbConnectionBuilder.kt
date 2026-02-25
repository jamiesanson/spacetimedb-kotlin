package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.protocol.Compression
import dev.sanson.spacetimedb.transport.WebSocketTransport
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer

/**
 * Fluent builder for establishing a [DbConnection].
 *
 * Example:
 * ```kotlin
 * val connection = DbConnection.builder()
 *     .withUri("http://localhost:3000")
 *     .withDatabaseName("my_db")
 *     .withToken(savedToken)
 *     .onConnect { identity, token, connectionId ->
 *         println("Connected as $identity")
 *     }
 *     .build(scope)
 * ```
 *
 * @see DbConnection
 */
public class DbConnectionBuilder internal constructor() {
    private var uri: String? = null
    private var databaseName: String? = null
    private var token: String? = null
    private var compression: Compression = Compression.None
    private var onConnect: ((Identity, String, ConnectionId) -> Unit)? = null
    private var onDisconnect: ((SpacetimeError?) -> Unit)? = null
    private var onConnectError: ((SpacetimeError) -> Unit)? = null
    private var tableDeserializers: Map<String, KSerializer<out Any>> = emptyMap()
    private var httpClient: HttpClient? = null

    /** Set the SpacetimeDB host URI (e.g. "http://localhost:3000"). */
    public fun withUri(uri: String): DbConnectionBuilder = apply {
        this.uri = uri
    }

    /** Set the database name to connect to. */
    public fun withDatabaseName(name: String): DbConnectionBuilder = apply {
        this.databaseName = name
    }

    /** Set the auth token for reconnection. */
    public fun withToken(token: String?): DbConnectionBuilder = apply {
        this.token = token
    }

    /** Set the preferred compression for server messages. */
    public fun withCompression(compression: Compression): DbConnectionBuilder = apply {
        this.compression = compression
    }

    /**
     * Register table deserializers for row decoding.
     *
     * Maps table names to their kotlinx.serialization [KSerializer].
     * This is typically called by generated code.
     */
    public fun withTableDeserializers(deserializers: Map<String, KSerializer<out Any>>): DbConnectionBuilder = apply {
        this.tableDeserializers = deserializers
    }

    /**
     * Provide a custom Ktor [HttpClient] for the WebSocket connection.
     *
     * Useful for testing or configuring custom engines/plugins.
     */
    public fun withHttpClient(client: HttpClient): DbConnectionBuilder = apply {
        this.httpClient = client
    }

    /**
     * Callback invoked when the connection is established and the server sends
     * the initial identity, token, and connection ID.
     */
    public fun onConnect(callback: (identity: Identity, token: String, connectionId: ConnectionId) -> Unit): DbConnectionBuilder = apply {
        this.onConnect = callback
    }

    /**
     * Callback invoked when the connection is closed, either normally or due to an error.
     *
     * @param callback Receives `null` for a clean disconnect, or a [SpacetimeError] for errors.
     */
    public fun onDisconnect(callback: (error: SpacetimeError?) -> Unit): DbConnectionBuilder = apply {
        this.onDisconnect = callback
    }

    /**
     * Callback invoked if the initial connection attempt fails.
     */
    public fun onConnectError(callback: (error: SpacetimeError) -> Unit): DbConnectionBuilder = apply {
        this.onConnectError = callback
    }

    /**
     * Establishes the WebSocket connection and starts the message loop.
     *
     * The message loop runs as a child coroutine of the provided [scope].
     * Cancel the scope or call [DbConnection.disconnect] to shut down.
     *
     * @param scope The [CoroutineScope] in which to run the message loop.
     * @return A connected [DbConnection] instance.
     * @throws SpacetimeError.FailedToConnect if the WebSocket connection fails.
     */
    public suspend fun build(scope: CoroutineScope): DbConnection {
        val uri = requireNotNull(uri) { "URI is required. Call withUri() before build()." }
        val dbName = requireNotNull(databaseName) { "Database name is required. Call withDatabaseName() before build()." }

        val transport = if (httpClient != null) {
            WebSocketTransport(httpClient!!)
        } else {
            WebSocketTransport()
        }

        val wsConnection = try {
            transport.connect(
                host = uri,
                databaseName = dbName,
                compression = compression,
                token = token,
            )
        } catch (e: Exception) {
            val error = SpacetimeError.FailedToConnect(e)
            onConnectError?.invoke(error)
            throw error
        }

        val connection = DbConnection(
            cache = ClientCache(),
            callbacks = DbCallbacks(),
            connection = wsConnection,
            scope = scope,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            tableDeserializers = tableDeserializers,
        )

        connection.start()
        return connection
    }
}
