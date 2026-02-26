package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.bsatn.Bsatn
import dev.sanson.spacetimedb.protocol.ClientMessage
import dev.sanson.spacetimedb.protocol.QuerySetId
import dev.sanson.spacetimedb.protocol.ReducerOutcome
import dev.sanson.spacetimedb.protocol.ServerMessage
import dev.sanson.spacetimedb.protocol.TransactionUpdate
import dev.sanson.spacetimedb.transport.WebSocketConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

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
    private val connector: suspend () -> WebSocketConnection,
    private val scope: CoroutineScope,
    private val onConnect: ((Identity, String, ConnectionId) -> Unit)?,
    private val onConnectError: ((SpacetimeError) -> Unit)?,
    private val onDisconnect: ((SpacetimeError?) -> Unit)?,
    private val tableDeserializers: Map<String, KSerializer<out Any>>,
    private val pkExtractors: Map<String, (Any) -> Any> = emptyMap(),
    private val logger: SpacetimeLogger = NoOpLogger,
) {
    private val subscriptions = mutableMapOf<QuerySetId, SubscriptionHandle>()
    private val reducerNames = mutableMapOf<UInt, String>()
    private val outgoing = Channel<ClientMessage>(Channel.UNLIMITED)

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
        logger.info("Connecting to SpacetimeDB...")

        messageLoopJob = scope.launch {
            val conn = try {
                connector()
            } catch (e: Exception) {
                isActive = false
                logger.error("Connection failed", e)
                onConnectError?.invoke(SpacetimeError.FailedToConnect(e))
                return@launch
            }

            logger.info("WebSocket connected, starting message loop")

            try {
                // Launch sender coroutine to forward queued messages
                val senderJob = launch {
                    for (msg in outgoing) {
                        conn.send(msg)
                    }
                }

                // Process incoming messages
                conn.serverMessages.collect { message ->
                    processMessage(message)
                }

                // Normal close
                senderJob.cancel()
                isActive = false
                logger.info("Connection closed normally")
                onDisconnect?.invoke(null)
            } catch (e: CancellationException) {
                isActive = false
                logger.info("Connection cancelled")
                onDisconnect?.invoke(null)
            } catch (e: Exception) {
                isActive = false
                logger.error("Connection error", e)
                onDisconnect?.invoke(SpacetimeError.Internal("Connection error", e))
            } finally {
                try {
                    conn.close()
                } catch (_: Exception) {
                    // Best-effort close
                }
            }
        }
    }

    private fun processMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.InitialConnection -> handleInitialConnection(message)
            is ServerMessage.SubscribeApplied -> handleSubscribeApplied(message)
            is ServerMessage.UnsubscribeApplied -> handleUnsubscribeApplied(message)
            is ServerMessage.SubscriptionError -> handleSubscriptionError(message)
            is ServerMessage.TransactionUpdateMsg -> handleTransactionUpdate(message.update, event = Event.Transaction)
            is ServerMessage.ReducerResult -> handleReducerResult(message)
            is ServerMessage.OneOffQueryResult -> { /* TODO: one-off query support */ }
            is ServerMessage.ProcedureResult -> { /* TODO: procedure result support */ }
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
