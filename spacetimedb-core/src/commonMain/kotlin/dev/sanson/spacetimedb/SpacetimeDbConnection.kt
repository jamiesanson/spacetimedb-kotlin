package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.bsatn.Bsatn
import dev.sanson.spacetimedb.protocol.ClientMessage
import dev.sanson.spacetimedb.protocol.ProcedureStatus
import dev.sanson.spacetimedb.protocol.QueryResult
import dev.sanson.spacetimedb.protocol.QuerySetId
import dev.sanson.spacetimedb.protocol.ReducerOutcome
import dev.sanson.spacetimedb.protocol.ServerMessage
import dev.sanson.spacetimedb.protocol.TransactionUpdate
import dev.sanson.spacetimedb.transport.WebSocketConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.intellij.lang.annotations.Language
import kotlin.jvm.JvmName

/**
 * An active connection to a SpacetimeDB database.
 *
 * Manages the WebSocket lifecycle, subscription tracking, client cache updates,
 * and callback dispatch. Obtain an instance via [SpacetimeDbConnectionBuilder.build].
 *
 * The connection runs a message-processing coroutine in the provided [CoroutineScope].
 * Cancel the scope or call [disconnect] to shut down the connection.
 */
public class SpacetimeDbConnection internal constructor(
    public val cache: ClientCache,
    public val callbacks: DbCallbacks,
    private val connector: suspend (token: String?) -> WebSocketConnection,
    private val scope: CoroutineScope,
    private val onConnect: ((Identity, String, ConnectionId) -> Unit)?,
    private val onConnectError: ((SpacetimeError) -> Unit)?,
    private val onDisconnect: ((SpacetimeError?) -> Unit)?,
    private val tableDeserializers: Map<String, KSerializer<out Any>>,
    private val pkExtractors: Map<String, (Any) -> Any> = emptyMap(),
    private val logger: SpacetimeLogger = NoOpLogger,
    private val reconnectConfig: ReconnectConfig? = null,
) {
    private val subscriptions = mutableMapOf<QuerySetId, SubscriptionHandle>()
    private val reducerNames = mutableMapOf<UInt, String>()
    private val pendingProcedures = mutableMapOf<UInt, CompletableDeferred<ProcedureOutcome>>()
    private val pendingOneOffQueries = mutableMapOf<UInt, CompletableDeferred<QueryResult>>()
    private val outgoing = Channel<ClientMessage>(Channel.UNLIMITED)
    private val savedSubscriptionQueries = mutableListOf<List<String>>()

    /** The identity assigned by the server, available after the initial handshake. */
    public var identity: Identity? = null
        private set

    /** The connection ID assigned by the server, available after the initial handshake. */
    public var connectionId: ConnectionId? = null
        private set

    /** The auth token assigned by the server, available after the initial handshake. */
    public var token: String? = null
        private set

    /** True while the message loop is running. */
    public var isActive: Boolean = false
        private set

    private var messageLoopJob: Job? = null

    /**
     * Create a new [SubscriptionBuilder] for subscribing to SQL queries.
     */
    public fun subscriptionBuilder(): SubscriptionBuilder =
        SubscriptionBuilder(
            sendChannel = outgoing,
            registerHandle = { handle ->
                subscriptions[handle.querySetId] = handle
            },
        )

    /**
     * Send a reducer call to the server.
     *
     * @param reducerName Name of the reducer to call
     * @param args BSATN-encoded arguments
     */
    public fun callReducer(reducerName: String, args: ByteArray) {
        val requestId = nextRequestId()
        reducerNames[requestId] = reducerName
        val msg = ClientMessage.CallReducer(
            requestId = requestId,
            flags = dev.sanson.spacetimedb.protocol.CallReducerFlags.Default,
            reducer = reducerName,
            args = args,
        )
        outgoing.trySend(msg)
    }

    /**
     * Call a procedure on the server and suspend until the result arrives.
     *
     * Procedures are non-transactional server functions that return values
     * directly, unlike reducers which produce side effects via subscriptions.
     *
     * @param procedureName Name of the procedure to call.
     * @param args BSATN-encoded arguments.
     * @return The [ProcedureOutcome] containing the return value or error.
     */
    public suspend fun callProcedure(procedureName: String, args: ByteArray): ProcedureOutcome {
        val requestId = nextRequestId()
        val deferred = CompletableDeferred<ProcedureOutcome>()
        pendingProcedures[requestId] = deferred
        val msg = ClientMessage.CallProcedure(
            requestId = requestId,
            flags = dev.sanson.spacetimedb.protocol.CallProcedureFlags.Default,
            procedure = procedureName,
            args = args,
        )
        outgoing.trySend(msg)
        return deferred.await()
    }

    /**
     * Call a procedure on the server and deserialize the return value.
     *
     * Suspends until the server responds. If the procedure returns a value,
     * it is decoded from BSATN using the provided [serializer]. If the server
     * reports an internal error, a [SpacetimeError.Internal] is thrown.
     *
     * @param T The expected return type.
     * @param procedureName Name of the procedure to call.
     * @param args BSATN-encoded arguments.
     * @param serializer Deserializer for the return value.
     * @return The deserialized return value.
     * @throws SpacetimeError.Internal if the procedure failed server-side.
     */
    public suspend fun <T> callProcedure(
        procedureName: String,
        args: ByteArray,
        serializer: KSerializer<T>,
    ): T {
        val outcome = callProcedure(procedureName, args)
        return when (outcome) {
            is ProcedureOutcome.Returned ->
                Bsatn.decodeFromByteArray(serializer, outcome.value)
            is ProcedureOutcome.InternalError ->
                throw SpacetimeError.Internal(outcome.message)
            else -> error("Unknown ProcedureOutcome: $outcome")
        }
    }

    /**
     * Call a procedure on the server and deserialize the return value.
     *
     * Convenience overload that infers the [KSerializer] from the reified type parameter.
     *
     * @see callProcedure
     */
    @JvmName("callProcedureTyped")
    public suspend inline fun <reified T> callProcedure(
        procedureName: String,
        args: ByteArray,
    ): T = callProcedure(procedureName, args, serializer())

    /**
     * Execute a one-off SQL query against the server and return the results.
     *
     * Unlike subscriptions, one-off queries return results immediately without
     * creating a persistent server-side query. Useful for ad-hoc queries,
     * dashboards, or data exploration outside the subscription model.
     *
     * Suspends until the server responds. If the server reports an error,
     * a [SpacetimeError.QueryError] is thrown.
     *
     * @param T The row type to deserialize results into.
     * @param sql The SQL query string.
     * @param serializer Deserializer for the row type.
     * @return The list of deserialized rows.
     * @throws SpacetimeError.QueryError if the server rejects the query.
     */
    public suspend fun <T> remoteQuery(@Language("SQL") sql: String, serializer: KSerializer<T>): List<T> {
        val requestId = nextRequestId()
        val deferred = CompletableDeferred<QueryResult>()
        pendingOneOffQueries[requestId] = deferred
        val msg = ClientMessage.OneOffQuery(
            requestId = requestId,
            queryString = sql,
        )
        outgoing.trySend(msg)

        return when (val result = deferred.await()) {
            is QueryResult.Ok -> {
                result.rows.tables.flatMap { table ->
                    table.rows.rows().map { bytes ->
                        Bsatn.decodeFromByteArray(serializer, bytes)
                    }
                }
            }
            is QueryResult.Err -> throw SpacetimeError.QueryError(result.error)
        }
    }

    /**
     * Execute a one-off SQL query against the server and return the results.
     *
     * Convenience overload that infers the [KSerializer] from the reified type parameter.
     *
     * @see remoteQuery
     */
    public suspend inline fun <reified T> remoteQuery(@Language("SQL") sql: String): List<T> =
        remoteQuery(sql, serializer())

    /**
     * Disconnect from the server and stop the message loop.
     */
    public fun disconnect() {
        messageLoopJob?.cancel()
    }

    /**
     * Starts the connection and message loop. Called internally by [SpacetimeDbConnectionBuilder].
     *
     * Connects to the server asynchronously. If the connection fails,
     * [onConnectError] is invoked and the connection becomes inactive.
     */
    internal fun start() {
        isActive = true

        messageLoopJob = scope.launch {
            var reconnectAttempt = 0
            var reconnectDelay = reconnectConfig?.initialDelay ?: kotlin.time.Duration.ZERO
            var isFirstConnect = true

            connectLoop@ while (true) {
                if (!isFirstConnect) {
                    logger.info("Reconnecting to SpacetimeDB (attempt $reconnectAttempt)...")
                } else {
                    logger.info("Connecting to SpacetimeDB...")
                }

                // Connect — use server-assigned token on reconnect
                val conn = try {
                    connector(if (isFirstConnect) null else token)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (reconnectConfig != null && reconnectAttempt < reconnectConfig.maxAttempts) {
                        logger.warn("Connection failed, retrying in $reconnectDelay")
                        delay(reconnectDelay)
                        reconnectDelay = minOf(reconnectDelay * 2, reconnectConfig.maxDelay)
                        reconnectAttempt++
                        continue@connectLoop
                    }
                    isActive = false
                    logger.error("Connection failed", e)
                    if (isFirstConnect) {
                        onConnectError?.invoke(SpacetimeError.FailedToConnect(e))
                    } else {
                        onDisconnect?.invoke(SpacetimeError.Internal("Reconnection failed after $reconnectAttempt attempts", e))
                    }
                    return@launch
                }

                // Connected — reset reconnect counters
                reconnectAttempt = 0
                reconnectDelay = reconnectConfig?.initialDelay ?: kotlin.time.Duration.ZERO

                logger.info("WebSocket connected, starting message loop")

                // If this is a reconnect, re-subscribe to previous queries
                if (!isFirstConnect) {
                    resubscribeAll()
                }
                isFirstConnect = false

                // Run the message loop
                var abnormalDisconnect = false
                try {
                    val senderJob = launch {
                        for (msg in outgoing) {
                            conn.send(msg)
                        }
                    }

                    conn.serverMessages.collect { message ->
                        processMessage(message)
                    }

                    // Normal server close
                    senderJob.cancel()
                    abnormalDisconnect = true // server closed unexpectedly
                } catch (e: CancellationException) {
                    // User-initiated disconnect — never reconnect
                    isActive = false
                    logger.info("Connection cancelled")
                    onDisconnect?.invoke(null)
                    return@launch
                } catch (e: Exception) {
                    logger.error("Connection error", e)
                    abnormalDisconnect = true
                } finally {
                    // Cancel pending procedure calls and one-off queries
                    val disconnectError = CancellationException("Connection closed")
                    pendingProcedures.values.forEach { it.cancel(disconnectError) }
                    pendingProcedures.clear()
                    pendingOneOffQueries.values.forEach { it.cancel(disconnectError) }
                    pendingOneOffQueries.clear()

                    try {
                        conn.close()
                    } catch (_: Exception) {
                        // Best-effort close
                    }
                }

                // Attempt reconnect if configured
                if (abnormalDisconnect && reconnectConfig != null) {
                    prepareForReconnect()
                    reconnectAttempt = 1
                    reconnectDelay = reconnectConfig.initialDelay
                    logger.info("Connection lost, reconnecting in $reconnectDelay")
                    delay(reconnectDelay)
                    reconnectDelay = minOf(reconnectDelay * 2, reconnectConfig.maxDelay)
                    continue@connectLoop
                }

                // No reconnect — report disconnect
                isActive = false
                logger.info("Connection closed normally")
                onDisconnect?.invoke(null)
                break@connectLoop
            }
        }
    }

    /**
     * Prepare internal state for a reconnection attempt.
     *
     * Saves active subscription queries, clears the cache and subscription state,
     * and drains the outgoing message queue.
     */
    private fun prepareForReconnect() {
        // Save queries from all non-ended subscriptions
        val queries = subscriptions.values
            .filter { !it.isEnded }
            .map { it.querySql }
        savedSubscriptionQueries.clear()
        savedSubscriptionQueries.addAll(queries)

        // Clear state
        subscriptions.clear()
        reducerNames.clear()
        cache.clear()

        // Drain outgoing channel (old messages are for the previous connection)
        while (outgoing.tryReceive().isSuccess) { /* drain */ }
    }

    /**
     * Re-subscribe to all queries that were active before the connection dropped.
     */
    private fun resubscribeAll() {
        for (queries in savedSubscriptionQueries) {
            val querySetId = nextQuerySetId()
            val handle = SubscriptionHandle(
                querySetId = querySetId,
                querySql = queries,
                sendChannel = outgoing,
                onApplied = null,
                onError = null,
            )
            subscriptions[querySetId] = handle
            val msg = ClientMessage.Subscribe(
                requestId = nextRequestId(),
                querySetId = querySetId,
                queryStrings = queries,
            )
            outgoing.trySend(msg)
        }
        if (savedSubscriptionQueries.isNotEmpty()) {
            logger.info("Re-subscribed to ${savedSubscriptionQueries.size} query set(s)")
        }
        savedSubscriptionQueries.clear()
    }

    private fun processMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.InitialConnection -> handleInitialConnection(message)
            is ServerMessage.SubscribeApplied -> handleSubscribeApplied(message)
            is ServerMessage.UnsubscribeApplied -> handleUnsubscribeApplied(message)
            is ServerMessage.SubscriptionError -> handleSubscriptionError(message)
            is ServerMessage.TransactionUpdateMsg -> handleTransactionUpdate(message.update, event = Event.Transaction)
            is ServerMessage.ReducerResult -> handleReducerResult(message)
            is ServerMessage.OneOffQueryResult -> handleOneOffQueryResult(message)
            is ServerMessage.ProcedureResult -> handleProcedureResult(message)
        }
    }

    private fun handleInitialConnection(msg: ServerMessage.InitialConnection) {
        identity = msg.identity
        connectionId = msg.connectionId
        token = msg.token
        logger.info("Identity: ${msg.identity}, ConnectionId: ${msg.connectionId}")
        onConnect?.invoke(msg.identity, msg.token, msg.connectionId)
    }

    private fun handleSubscribeApplied(msg: ServerMessage.SubscribeApplied) {
        logger.debug("SubscribeApplied for querySet=${msg.querySetId}")
        // Apply the initial row data to the cache
        for (tableRows in msg.rows.tables) {
            applySubscribeRows(tableRows.table, tableRows.rows)
        }

        // Notify the subscription handle and invoke callback
        val handle = subscriptions[msg.querySetId]
        val callback = handle?.notifyApplied()
        callback?.invoke()
    }

    private fun handleUnsubscribeApplied(msg: ServerMessage.UnsubscribeApplied) {
        // Apply dropped rows if present
        if (msg.rows != null) {
            for (tableRows in msg.rows.tables) {
                applyUnsubscribeRows(tableRows.table, tableRows.rows)
            }
        }

        val handle = subscriptions.remove(msg.querySetId)
        val callback = handle?.notifyEnded()
        callback?.invoke()
    }

    private fun handleSubscriptionError(msg: ServerMessage.SubscriptionError) {
        logger.warn("SubscriptionError for querySet=${msg.querySetId}: ${msg.error}")
        val handle = subscriptions.remove(msg.querySetId)
        val callback = handle?.notifyError(msg.error)
        callback?.invoke()
    }

    private fun handleTransactionUpdate(update: TransactionUpdate, event: Event<*>) {
        for (querySetUpdate in update.querySets) {
            for (tableUpdate in querySetUpdate.tables) {
                applyTableUpdate(tableUpdate, event)
            }
        }
    }

    private fun handleReducerResult(msg: ServerMessage.ReducerResult) {
        val reducerName = reducerNames.remove(msg.requestId)
        when (val result = msg.result) {
            is ReducerOutcome.Ok -> {
                val event = if (reducerName != null) {
                    Event.Reducer(
                        ReducerEvent(
                            timestamp = msg.timestamp,
                            status = Status.Committed,
                            reducer = reducerName,
                        )
                    )
                } else {
                    Event.Transaction
                }
                handleTransactionUpdate(result.transactionUpdate, event)
                if (reducerName != null) {
                    callbacks.invokeReducerCallbacks(
                        reducerName,
                        (event as Event.Reducer).event,
                    )
                }
            }
            is ReducerOutcome.OkEmpty -> {
                if (reducerName != null) {
                    val event = ReducerEvent(
                        timestamp = msg.timestamp,
                        status = Status.Committed,
                        reducer = reducerName,
                    )
                    callbacks.invokeReducerCallbacks(reducerName, event)
                }
            }
            is ReducerOutcome.Err -> {
                if (reducerName != null) {
                    val event = ReducerEvent(
                        timestamp = msg.timestamp,
                        status = Status.Failed(result.error.decodeToString()),
                        reducer = reducerName,
                    )
                    callbacks.invokeReducerCallbacks(reducerName, event)
                }
            }
            is ReducerOutcome.InternalError -> {
                if (reducerName != null) {
                    val event = ReducerEvent(
                        timestamp = msg.timestamp,
                        status = Status.Panic(result.message),
                        reducer = reducerName,
                    )
                    callbacks.invokeReducerCallbacks(reducerName, event)
                }
            }
        }
    }

    private fun handleProcedureResult(msg: ServerMessage.ProcedureResult) {
        val deferred = pendingProcedures.remove(msg.requestId) ?: return
        val outcome = when (val status = msg.status) {
            is ProcedureStatus.Returned -> ProcedureOutcome.Returned(
                value = status.value,
                timestamp = msg.timestamp,
                executionDuration = msg.totalHostExecutionDuration,
            )
            is ProcedureStatus.InternalError -> ProcedureOutcome.InternalError(
                message = status.message,
                timestamp = msg.timestamp,
                executionDuration = msg.totalHostExecutionDuration,
            )
        }
        deferred.complete(outcome)
    }

    private fun handleOneOffQueryResult(msg: ServerMessage.OneOffQueryResult) {
        val deferred = pendingOneOffQueries.remove(msg.requestId) ?: return
        deferred.complete(msg.result)
    }

    @Suppress("UNCHECKED_CAST")
    private fun applySubscribeRows(
        tableName: String,
        rows: dev.sanson.spacetimedb.protocol.BsatnRowList,
    ) {
        val serializer = tableDeserializers[tableName] ?: return
        val tableCache = cache.getOrCreateTable<Any>(tableName)
        for (rowBytes in rows.rows()) {
            val row = Bsatn.decodeFromByteArray(serializer as KSerializer<Any>, rowBytes)
            tableCache.insert(rowBytes, row)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyUnsubscribeRows(
        tableName: String,
        rows: dev.sanson.spacetimedb.protocol.BsatnRowList,
    ) {
        val tableCache = cache.getTable<Any>(tableName) ?: return
        for (rowBytes in rows.rows()) {
            tableCache.delete(rowBytes)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyTableUpdate(
        wireUpdate: dev.sanson.spacetimedb.protocol.TableUpdate,
        event: Event<*>,
    ) {
        val tableName = wireUpdate.tableName
        val serializer = tableDeserializers[tableName] ?: run {
            logger.warn("No deserializer for table '$tableName', skipping update")
            return
        }
        val tableCache = cache.getOrCreateTable<Any>(tableName)

        for (rows in wireUpdate.rows) {
            when (rows) {
                is dev.sanson.spacetimedb.protocol.TableUpdateRows.PersistentTable -> {
                    val inserts = rows.inserts.rows().map { bytes ->
                        RowWithBsatn(
                            bsatn = bytes,
                            row = Bsatn.decodeFromByteArray(serializer as KSerializer<Any>, bytes),
                        )
                    }
                    val deletes = rows.deletes.rows().map { bytes ->
                        RowWithBsatn(
                            bsatn = bytes,
                            row = Bsatn.decodeFromByteArray(serializer as KSerializer<Any>, bytes),
                        )
                    }
                    val update = TableUpdate(inserts = inserts, deletes = deletes)
                    if (!update.isEmpty) {
                        val diff = tableCache.applyDiff(update)
                        val refinedDiff = pkExtractors[tableName]?.let { getPk ->
                            diff.withUpdatesByPk(getPk)
                        } ?: diff
                        callbacks.invokeCallbacks(tableName, refinedDiff, event)
                    }
                }
                is dev.sanson.spacetimedb.protocol.TableUpdateRows.EventTable -> {
                    // Event tables: decode and invoke event callbacks
                    for (bytes in rows.events.rows()) {
                        val row = Bsatn.decodeFromByteArray(serializer as KSerializer<Any>, bytes)
                        val diff = TableAppliedDiff<Any>(
                            inserts = listOf(row),
                            deletes = emptyList(),
                        )
                        callbacks.invokeCallbacks(tableName, diff, event)
                    }
                }
            }
        }
    }

    public companion object {
        /** Create a new [SpacetimeDbConnectionBuilder]. */
        public fun builder(): SpacetimeDbConnectionBuilder = SpacetimeDbConnectionBuilder()
    }
}
