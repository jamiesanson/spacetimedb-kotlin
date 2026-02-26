package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.protocol.Compression
import dev.sanson.spacetimedb.transport.WebSocketTransport
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer

/**
 * Fluent builder for establishing a [SpacetimeDbConnection].
 *
 * Example:
 * ```kotlin
 * val connection = SpacetimeDbConnection.builder()
 *     .withUri("http://localhost:3000")
 *     .withDatabaseName("my_db")
 *     .withToken(savedToken)
 *     .onConnect { identity, token, connectionId ->
 *         println("Connected as $identity")
 *     }
 *     .build(scope)
 * ```
 *
 * @see SpacetimeDbConnection
 */
public class SpacetimeDbConnectionBuilder() {
    private var uri: String? = null
    private var databaseName: String? = null
    private var token: String? = null
    private var compression: Compression = Compression.None
    private var onConnect: ((Identity, String, ConnectionId) -> Unit)? = null
    private var onDisconnect: ((SpacetimeError?) -> Unit)? = null
    private var onConnectError: ((SpacetimeError) -> Unit)? = null
    private var tableDeserializers: Map<String, KSerializer<out Any>> = emptyMap()
    private var pkExtractors: Map<String, (Any) -> Any> = emptyMap()
    private var httpClient: HttpClient? = null
    private var logger: SpacetimeLogger = NoOpLogger

    /** Set the SpacetimeDB host URI (e.g. "http://localhost:3000"). */
    public fun withUri(uri: String): SpacetimeDbConnectionBuilder = apply {
        this.uri = uri
    }

    /** Set the database name to connect to. */
    public fun withDatabaseName(name: String): SpacetimeDbConnectionBuilder = apply {
        this.databaseName = name
    }

    /** Set the auth token for reconnection. */
    public fun withToken(token: String?): SpacetimeDbConnectionBuilder = apply {
        this.token = token
    }

    /** Set the preferred compression for server messages. */
    public fun withCompression(compression: Compression): SpacetimeDbConnectionBuilder = apply {
        this.compression = compression
    }

    /**
     * Register table deserializers for row decoding.
     *
     * Maps table names to their kotlinx.serialization [KSerializer].
     * This is typically called by generated code.
     */
    public fun withTableDeserializers(deserializers: Map<String, KSerializer<out Any>>): SpacetimeDbConnectionBuilder = apply {
        this.tableDeserializers = deserializers
    }

    /**
     * Register primary-key extractor functions for tables.
     *
     * Maps table names to a function that extracts the primary-key value from a row.
     * Used to match delete+insert pairs as "update" events. Typically called by generated code.
     */
    public fun withPkExtractors(extractors: Map<String, (Any) -> Any>): SpacetimeDbConnectionBuilder = apply {
        this.pkExtractors = extractors
    }

    /**
     * Provide a custom Ktor [HttpClient] for the WebSocket connection.
     *
     * Useful for testing or configuring custom engines/plugins.
     */
    public fun withHttpClient(client: HttpClient): SpacetimeDbConnectionBuilder = apply {
        this.httpClient = client
    }

    /**
     * Set a [SpacetimeLogger] for SDK diagnostic messages.
     *
     * Defaults to [NoOpLogger]. Use [PrintLogger] for quick debugging.
     */
    public fun withLogger(logger: SpacetimeLogger): SpacetimeDbConnectionBuilder = apply {
        this.logger = logger
    }

    /**
     * Callback invoked when the connection is established and the server sends
     * the initial identity, token, and connection ID.
     */
    public fun onConnect(callback: (identity: Identity, token: String, connectionId: ConnectionId) -> Unit): SpacetimeDbConnectionBuilder = apply {
        this.onConnect = callback
    }

    /**
     * Callback invoked when the connection is closed, either normally or due to an error.
     *
     * @param callback Receives `null` for a clean disconnect, or a [SpacetimeError] for errors.
     */
    public fun onDisconnect(callback: (error: SpacetimeError?) -> Unit): SpacetimeDbConnectionBuilder = apply {
        this.onDisconnect = callback
    }

    /**
     * Callback invoked if the initial connection attempt fails.
     */
    public fun onConnectError(callback: (error: SpacetimeError) -> Unit): SpacetimeDbConnectionBuilder = apply {
        this.onConnectError = callback
    }

    /**
     * Creates the connection and starts connecting asynchronously.
     *
     * The WebSocket connection is established in a coroutine launched in the
     * provided [scope]. If the connection fails, [onConnectError] is invoked.
     * Cancel the scope or call [SpacetimeDbConnection.disconnect] to shut down.
     *
     * @param scope The [CoroutineScope] in which to run the connection and message loop.
     * @return A [SpacetimeDbConnection] instance that is connecting in the background.
     */
    public fun build(scope: CoroutineScope): SpacetimeDbConnection {
        val uri = requireNotNull(uri) { "URI is required. Call withUri() before build()." }
        val dbName = requireNotNull(databaseName) { "Database name is required. Call withDatabaseName() before build()." }

        val transport = if (httpClient != null) {
            WebSocketTransport(httpClient!!)
        } else {
            WebSocketTransport()
        }

        val connection = SpacetimeDbConnection(
            cache = ClientCache(),
            callbacks = DbCallbacks(),
            connector = {
                transport.connect(
                    host = uri,
                    databaseName = dbName,
                    compression = compression,
                    token = token,
                )
            },
            scope = scope,
            onConnect = onConnect,
            onConnectError = onConnectError,
            onDisconnect = onDisconnect,
            tableDeserializers = tableDeserializers,
            pkExtractors = pkExtractors,
            logger = logger,
        )

        connection.start()
        return connection
    }
}
