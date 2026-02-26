package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.protocol.Compression
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer

/**
 * Scope-control marker that prevents accidentally accessing an outer DSL receiver
 * from within a nested lambda.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
internal annotation class SpacetimeDbDsl

/**
 * DSL receiver for configuring a [SpacetimeDbConnection].
 *
 * Example:
 * ```kotlin
 * val conn = SpacetimeDbConnection(scope) {
 *     uri = "http://localhost:3000"
 *     databaseName = "my_db"
 *     token = savedToken
 *
 *     onConnect { identity, token, connectionId ->
 *         println("Connected as $identity")
 *     }
 *     onDisconnect { error ->
 *         println("Disconnected: $error")
 *     }
 * }
 * ```
 *
 * @see SpacetimeDbConnection
 * @see SpacetimeDbConnectionBuilder
 */
@SpacetimeDbDsl
public class SpacetimeDbConnectionDsl internal constructor() {

    /** SpacetimeDB host URI (e.g. "http://localhost:3000"). Required. */
    public var uri: String? = null

    /** Database name to connect to. Required. */
    public var databaseName: String? = null

    /** Auth token for reconnection. */
    public var token: String? = null

    /** Preferred compression for server messages. Defaults to [Compression.None]. */
    public var compression: Compression = Compression.None

    /** Custom Ktor [HttpClient] for the WebSocket connection. */
    public var httpClient: HttpClient? = null

    /** Logger for SDK diagnostic messages. Defaults to [NoOpLogger]. */
    public var logger: SpacetimeLogger = NoOpLogger

    /**
     * Table deserializers for row decoding.
     *
     * Maps table names to their kotlinx.serialization [KSerializer].
     * Typically set by generated code.
     */
    public var tableDeserializers: Map<String, KSerializer<out Any>> = emptyMap()

    /**
     * Primary-key extractor functions for tables.
     *
     * Maps table names to a function that extracts the primary-key value from a row.
     * Typically set by generated code.
     */
    public var pkExtractors: Map<String, (Any) -> Any> = emptyMap()

    private var onConnect: ((Identity, String, ConnectionId) -> Unit)? = null
    private var onDisconnect: ((SpacetimeError?) -> Unit)? = null
    private var onConnectError: ((SpacetimeError) -> Unit)? = null

    /**
     * Callback invoked when the connection is established and the server sends
     * the initial identity, token, and connection ID.
     */
    public fun onConnect(callback: (identity: Identity, token: String, connectionId: ConnectionId) -> Unit) {
        this.onConnect = callback
    }

    /**
     * Callback invoked when the connection is closed, either normally or due to an error.
     *
     * Receives `null` for a clean disconnect, or a [SpacetimeError] for errors.
     */
    public fun onDisconnect(callback: (error: SpacetimeError?) -> Unit) {
        this.onDisconnect = callback
    }

    /**
     * Callback invoked if the initial connection attempt fails.
     */
    public fun onConnectError(callback: (error: SpacetimeError) -> Unit) {
        this.onConnectError = callback
    }

    internal fun build(scope: CoroutineScope): SpacetimeDbConnection =
        SpacetimeDbConnectionBuilder()
            .apply {
                this@SpacetimeDbConnectionDsl.uri?.let { withUri(it) }
                this@SpacetimeDbConnectionDsl.databaseName?.let { withDatabaseName(it) }
                this@SpacetimeDbConnectionDsl.token?.let { withToken(it) }
                withCompression(this@SpacetimeDbConnectionDsl.compression)
                withTableDeserializers(this@SpacetimeDbConnectionDsl.tableDeserializers)
                withPkExtractors(this@SpacetimeDbConnectionDsl.pkExtractors)
                this@SpacetimeDbConnectionDsl.httpClient?.let { withHttpClient(it) }
                withLogger(this@SpacetimeDbConnectionDsl.logger)
                this@SpacetimeDbConnectionDsl.onConnect?.let { onConnect(it) }
                this@SpacetimeDbConnectionDsl.onDisconnect?.let { onDisconnect(it) }
                this@SpacetimeDbConnectionDsl.onConnectError?.let { onConnectError(it) }
            }
            .build(scope)
}

/**
 * Creates and starts a [SpacetimeDbConnection] using a DSL-style builder.
 *
 * ```kotlin
 * val conn = SpacetimeDbConnection(scope) {
 *     uri = "http://localhost:3000"
 *     databaseName = "my_db"
 *     token = savedToken
 *     logger = PrintLogger(level = SpacetimeLogger.Level.Debug)
 *
 *     onConnect { identity, token, connectionId ->
 *         println("Connected as $identity")
 *     }
 * }
 * ```
 *
 * The existing [SpacetimeDbConnectionBuilder] fluent API remains available
 * for Java interop and cases where method chaining is preferred.
 *
 * @param scope The [CoroutineScope] in which to run the connection and message loop.
 * @param block DSL configuration block.
 * @return A [SpacetimeDbConnection] that is connecting in the background.
 * @throws IllegalArgumentException if [SpacetimeDbConnectionDsl.uri] or
 *   [SpacetimeDbConnectionDsl.databaseName] is not set.
 */
public fun SpacetimeDbConnection(
    scope: CoroutineScope,
    block: SpacetimeDbConnectionDsl.() -> Unit,
): SpacetimeDbConnection =
    SpacetimeDbConnectionDsl().apply(block).build(scope)
