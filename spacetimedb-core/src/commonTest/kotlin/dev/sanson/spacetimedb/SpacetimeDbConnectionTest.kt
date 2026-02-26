package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.bsatn.Bsatn
import dev.sanson.spacetimedb.bsatn.U128
import dev.sanson.spacetimedb.bsatn.U256
import dev.sanson.spacetimedb.protocol.BsatnRowList
import dev.sanson.spacetimedb.protocol.ClientMessage
import dev.sanson.spacetimedb.protocol.ProcedureStatus
import dev.sanson.spacetimedb.protocol.QueryRows
import dev.sanson.spacetimedb.protocol.QuerySetId
import dev.sanson.spacetimedb.protocol.QuerySetUpdate
import dev.sanson.spacetimedb.protocol.ReducerOutcome
import dev.sanson.spacetimedb.protocol.RowSizeHint
import dev.sanson.spacetimedb.protocol.ServerMessage
import dev.sanson.spacetimedb.protocol.SingleTableRows
import dev.sanson.spacetimedb.protocol.TableUpdate as WireTableUpdate
import dev.sanson.spacetimedb.protocol.TableUpdateRows
import dev.sanson.spacetimedb.protocol.TransactionUpdate
import dev.sanson.spacetimedb.transport.WebSocketConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SpacetimeDbConnectionTest {

    @Serializable
    data class User(val id: UInt, val name: String)

    // -- Helpers --

    /**
     * Fake WebSocket connection that allows injecting server messages
     * and capturing outgoing client messages.
     */
    private class FakeWebSocketConnection {
        val incoming = Channel<ServerMessage>(Channel.UNLIMITED)
        val outgoing = Channel<ClientMessage>(Channel.UNLIMITED)

        fun asWebSocketConnection(): WebSocketConnection =
            FakeConnection(incoming.consumeAsFlow(), outgoing)

        fun sendToClient(message: ServerMessage) {
            incoming.trySend(message)
        }

        fun closeServer() {
            incoming.close()
        }
    }

    private class FakeConnection(
        override val serverMessages: Flow<ServerMessage>,
        private val outgoing: Channel<ClientMessage>,
    ) : WebSocketConnection() {
        override suspend fun send(message: ClientMessage) {
            outgoing.send(message)
        }

        override suspend fun close() {
            // no-op
        }
    }

    private fun encodeUser(user: User): ByteArray =
        Bsatn.encodeToByteArray(User.serializer(), user)

    private fun bsatnRowList(vararg rows: ByteArray): BsatnRowList {
        val offsets = mutableListOf<ULong>()
        var offset = 0UL
        for (row in rows) {
            offsets.add(offset)
            offset += row.size.toULong()
        }
        return BsatnRowList(
            sizeHint = RowSizeHint.RowOffsets(offsets),
            rowsData = rows.fold(byteArrayOf()) { acc, bytes -> acc + bytes },
        )
    }

    private fun createConnection(
        fake: FakeWebSocketConnection,
        scope: kotlinx.coroutines.CoroutineScope,
        onConnect: ((Identity, String, ConnectionId) -> Unit)? = null,
        onDisconnect: ((SpacetimeError?) -> Unit)? = null,
        reconnectConfig: ReconnectConfig? = null,
    ): SpacetimeDbConnection {
        val connection = SpacetimeDbConnection(
            cache = ClientCache(),
            callbacks = DbCallbacks(),
            connector = { _ -> fake.asWebSocketConnection() },
            scope = scope,
            onConnect = onConnect,
            onConnectError = null,
            onDisconnect = onDisconnect,
            tableDeserializers = mapOf("users" to User.serializer()),
            reconnectConfig = reconnectConfig,
        )
        connection.start()
        return connection
    }

    // -- InitialConnection --

    @Test
    fun `initial connection sets identity and token`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        var receivedIdentity: Identity? = null
        var receivedToken: String? = null
        var receivedConnectionId: ConnectionId? = null

        val conn = createConnection(fake, backgroundScope, onConnect = { identity, token, connectionId ->
            receivedIdentity = identity
            receivedToken = token
            receivedConnectionId = connectionId
        })

        val identity = Identity(U256(1u, 0u, 0u, 0u))
        val connectionId = ConnectionId(U128(4u, 3u))
        fake.sendToClient(ServerMessage.InitialConnection(identity, connectionId, "test-token"))

        // Let the message loop process

        assertEquals(identity, conn.identity)
        assertEquals(connectionId, conn.connectionId)
        assertEquals("test-token", conn.token)
        assertEquals(identity, receivedIdentity)
        assertEquals("test-token", receivedToken)
        assertEquals(connectionId, receivedConnectionId)

    }

    // -- SubscribeApplied --

    @Test
    fun `subscribe applied populates cache`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val alice = User(1u, "alice")
        val bob = User(2u, "bob")

        // Subscribe
        val handle = conn.subscriptionBuilder()
            .subscribe("SELECT * FROM users")

        // Server applies subscription
        fake.sendToClient(
            ServerMessage.SubscribeApplied(
                requestId = 1u,
                querySetId = handle.querySetId,
                rows = QueryRows(
                    tables = listOf(
                        SingleTableRows(
                            table = "users",
                            rows = bsatnRowList(encodeUser(alice), encodeUser(bob)),
                        )
                    )
                ),
            )
        )

        val users = conn.cache.getTable<User>("users")
        assertNotNull(users)
        assertEquals(2, users.count)
        assertTrue(handle.isActive)
    }

    @Test
    fun `subscribe applied invokes onApplied callback`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        var applied = false
        val handle = conn.subscriptionBuilder()
            .onApplied { applied = true }
            .subscribe("SELECT * FROM users")

        fake.sendToClient(
            ServerMessage.SubscribeApplied(
                requestId = 1u,
                querySetId = handle.querySetId,
                rows = QueryRows(tables = emptyList()),
            )
        )

        assertTrue(applied)
    }

    // -- SubscriptionError --

    @Test
    fun `subscription error invokes onError callback`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        var errorMsg: String? = null
        val handle = conn.subscriptionBuilder()
            .onError { errorMsg = it }
            .subscribe("SELECT * FROM invalid")

        fake.sendToClient(
            ServerMessage.SubscriptionError(
                requestId = 1u,
                querySetId = handle.querySetId,
                error = "table not found",
            )
        )

        assertEquals("table not found", errorMsg)
        assertTrue(handle.isEnded)
    }

    // -- TransactionUpdate --

    @Test
    fun `transaction update applies inserts to cache`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)
        val querySetId = QuerySetId(99u)

        val alice = User(1u, "alice")

        fake.sendToClient(
            ServerMessage.TransactionUpdateMsg(
                update = TransactionUpdate(
                    querySets = listOf(
                        QuerySetUpdate(
                            querySetId = querySetId,
                            tables = listOf(
                                WireTableUpdate(
                                    tableName = "users",
                                    rows = listOf(
                                        TableUpdateRows.PersistentTable(
                                            inserts = bsatnRowList(encodeUser(alice)),
                                            deletes = bsatnRowList(),
                                        )
                                    ),
                                )
                            ),
                        )
                    ),
                )
            )
        )

        val users = conn.cache.getTable<User>("users")
        assertNotNull(users)
        assertEquals(1, users.count)
        assertEquals(alice, users.iterator().next())

    }

    @Test
    fun `transaction update invokes row callbacks`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)
        val querySetId = QuerySetId(99u)

        val inserted = mutableListOf<User>()
        conn.callbacks.registerOnInsert<User>("users") { _, row -> inserted.add(row) }

        val alice = User(1u, "alice")
        fake.sendToClient(
            ServerMessage.TransactionUpdateMsg(
                update = TransactionUpdate(
                    querySets = listOf(
                        QuerySetUpdate(
                            querySetId = querySetId,
                            tables = listOf(
                                WireTableUpdate(
                                    tableName = "users",
                                    rows = listOf(
                                        TableUpdateRows.PersistentTable(
                                            inserts = bsatnRowList(encodeUser(alice)),
                                            deletes = bsatnRowList(),
                                        )
                                    ),
                                )
                            ),
                        )
                    ),
                )
            )
        )

        assertEquals(listOf(alice), inserted)

    }

    // -- ReducerResult --

    @Test
    fun `reducer result with Ok applies transaction update`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)
        val querySetId = QuerySetId(99u)

        val alice = User(1u, "alice")
        fake.sendToClient(
            ServerMessage.ReducerResult(
                requestId = 1u,
                timestamp = Timestamp.UNIX_EPOCH,
                result = ReducerOutcome.Ok(
                    retValue = byteArrayOf(),
                    transactionUpdate = TransactionUpdate(
                        querySets = listOf(
                            QuerySetUpdate(
                                querySetId = querySetId,
                                tables = listOf(
                                    WireTableUpdate(
                                        tableName = "users",
                                        rows = listOf(
                                            TableUpdateRows.PersistentTable(
                                                inserts = bsatnRowList(encodeUser(alice)),
                                                deletes = bsatnRowList(),
                                            )
                                        ),
                                    )
                                ),
                            )
                        ),
                    ),
                ),
            )
        )

        val users = conn.cache.getTable<User>("users")
        assertNotNull(users)
        assertEquals(1, users.count)
    }

    @Test
    fun `reducer result passes ReducerEvent to row callbacks`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val receivedEvents = mutableListOf<Event<*>>()
        conn.callbacks.registerOnInsert<User>("users") { event, _ -> receivedEvents.add(event) }

        // Call reducer to register the request ID mapping
        conn.callReducer("create_user", byteArrayOf())

        // Capture the request ID from the outgoing message
        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallReducer>(outgoing)
        val requestId = outgoing.requestId

        val alice = User(1u, "alice")
        fake.sendToClient(
            ServerMessage.ReducerResult(
                requestId = requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                result = ReducerOutcome.Ok(
                    retValue = byteArrayOf(),
                    transactionUpdate = TransactionUpdate(
                        querySets = listOf(
                            QuerySetUpdate(
                                querySetId = QuerySetId(99u),
                                tables = listOf(
                                    WireTableUpdate(
                                        tableName = "users",
                                        rows = listOf(
                                            TableUpdateRows.PersistentTable(
                                                inserts = bsatnRowList(encodeUser(alice)),
                                                deletes = bsatnRowList(),
                                            )
                                        ),
                                    )
                                ),
                            )
                        ),
                    ),
                ),
            )
        )

        assertEquals(1, receivedEvents.size)
        val event = receivedEvents.first()
        assertIs<Event.Reducer<*>>(event)
        assertEquals("create_user", event.event.reducer)
        assertEquals(Status.Committed, event.event.status)
    }

    @Test
    fun `reducer result with OkEmpty does not modify cache`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        fake.sendToClient(
            ServerMessage.ReducerResult(
                requestId = 1u,
                timestamp = Timestamp.UNIX_EPOCH,
                result = ReducerOutcome.OkEmpty,
            )
        )

        assertNull(conn.cache.getTable<User>("users"))

    }

    // -- Reducer callbacks --

    @Test
    fun `reducer result Ok fires reducer callback with Committed`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val receivedEvents = mutableListOf<ReducerEvent<*>>()
        conn.callbacks.registerOnReducer("create_user") { receivedEvents.add(it) }

        // Call reducer to register request ID mapping
        conn.callReducer("create_user", byteArrayOf())
        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallReducer>(outgoing)

        fake.sendToClient(
            ServerMessage.ReducerResult(
                requestId = outgoing.requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                result = ReducerOutcome.Ok(
                    retValue = byteArrayOf(),
                    transactionUpdate = TransactionUpdate(querySets = emptyList()),
                ),
            )
        )

        assertEquals(1, receivedEvents.size)
        assertEquals(Status.Committed, receivedEvents.first().status)
        assertEquals("create_user", receivedEvents.first().reducer)
    }

    @Test
    fun `reducer result Ok fires row callbacks then reducer callback`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val callOrder = mutableListOf<String>()
        conn.callbacks.registerOnInsert<User>("users") { _, _ -> callOrder.add("row") }
        conn.callbacks.registerOnReducer("create_user") { callOrder.add("reducer") }

        conn.callReducer("create_user", byteArrayOf())
        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallReducer>(outgoing)

        val alice = User(1u, "alice")
        fake.sendToClient(
            ServerMessage.ReducerResult(
                requestId = outgoing.requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                result = ReducerOutcome.Ok(
                    retValue = byteArrayOf(),
                    transactionUpdate = TransactionUpdate(
                        querySets = listOf(
                            QuerySetUpdate(
                                querySetId = QuerySetId(99u),
                                tables = listOf(
                                    WireTableUpdate(
                                        tableName = "users",
                                        rows = listOf(
                                            TableUpdateRows.PersistentTable(
                                                inserts = bsatnRowList(encodeUser(alice)),
                                                deletes = bsatnRowList(),
                                            )
                                        ),
                                    )
                                ),
                            )
                        ),
                    ),
                ),
            )
        )

        assertEquals(listOf("row", "reducer"), callOrder)
    }

    @Test
    fun `reducer result OkEmpty fires reducer callback with Committed`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val receivedEvents = mutableListOf<ReducerEvent<*>>()
        conn.callbacks.registerOnReducer("noop") { receivedEvents.add(it) }

        conn.callReducer("noop", byteArrayOf())
        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallReducer>(outgoing)

        fake.sendToClient(
            ServerMessage.ReducerResult(
                requestId = outgoing.requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                result = ReducerOutcome.OkEmpty,
            )
        )

        assertEquals(1, receivedEvents.size)
        assertEquals(Status.Committed, receivedEvents.first().status)
        assertEquals("noop", receivedEvents.first().reducer)
    }

    @Test
    fun `reducer result Err fires reducer callback with Failed`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val receivedEvents = mutableListOf<ReducerEvent<*>>()
        conn.callbacks.registerOnReducer("bad_reducer") { receivedEvents.add(it) }

        conn.callReducer("bad_reducer", byteArrayOf())
        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallReducer>(outgoing)

        fake.sendToClient(
            ServerMessage.ReducerResult(
                requestId = outgoing.requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                result = ReducerOutcome.Err("validation failed".encodeToByteArray()),
            )
        )

        assertEquals(1, receivedEvents.size)
        val status = receivedEvents.first().status
        assertIs<Status.Failed>(status)
        assertEquals("validation failed", status.message)
        // Err should not modify cache
        assertNull(conn.cache.getTable<User>("users"))
    }

    @Test
    fun `reducer result InternalError fires reducer callback with Panic`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val receivedEvents = mutableListOf<ReducerEvent<*>>()
        conn.callbacks.registerOnReducer("panic_reducer") { receivedEvents.add(it) }

        conn.callReducer("panic_reducer", byteArrayOf())
        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallReducer>(outgoing)

        fake.sendToClient(
            ServerMessage.ReducerResult(
                requestId = outgoing.requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                result = ReducerOutcome.InternalError("wasm trap"),
            )
        )

        assertEquals(1, receivedEvents.size)
        val status = receivedEvents.first().status
        assertIs<Status.Panic>(status)
        assertEquals("wasm trap", status.message)
        // InternalError should not modify cache
        assertNull(conn.cache.getTable<User>("users"))
    }

    @Test
    fun `reducer result without known request ID does not fire reducer callback`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val receivedEvents = mutableListOf<ReducerEvent<*>>()
        conn.callbacks.registerOnReducer("unknown") { receivedEvents.add(it) }

        // Send a ReducerResult without calling the reducer first (unknown request ID)
        fake.sendToClient(
            ServerMessage.ReducerResult(
                requestId = 9999u,
                timestamp = Timestamp.UNIX_EPOCH,
                result = ReducerOutcome.OkEmpty,
            )
        )

        assertEquals(0, receivedEvents.size)
    }

    @Test
    fun `row callbacks receive Event_Reducer for own reducer and Event_Transaction for others`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val receivedEvents = mutableListOf<Event<*>>()
        conn.callbacks.registerOnInsert<User>("users") { event, _ -> receivedEvents.add(event) }

        val alice = User(1u, "alice")
        val bob = User(2u, "bob")

        // Another client's change comes as TransactionUpdate
        fake.sendToClient(
            ServerMessage.TransactionUpdateMsg(
                update = TransactionUpdate(
                    querySets = listOf(
                        QuerySetUpdate(
                            querySetId = QuerySetId(99u),
                            tables = listOf(
                                WireTableUpdate(
                                    tableName = "users",
                                    rows = listOf(
                                        TableUpdateRows.PersistentTable(
                                            inserts = bsatnRowList(encodeUser(alice)),
                                            deletes = bsatnRowList(),
                                        )
                                    ),
                                )
                            ),
                        )
                    ),
                )
            )
        )

        // Our own reducer's change comes as ReducerResult
        conn.callReducer("add_user", byteArrayOf())
        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallReducer>(outgoing)

        fake.sendToClient(
            ServerMessage.ReducerResult(
                requestId = outgoing.requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                result = ReducerOutcome.Ok(
                    retValue = byteArrayOf(),
                    transactionUpdate = TransactionUpdate(
                        querySets = listOf(
                            QuerySetUpdate(
                                querySetId = QuerySetId(99u),
                                tables = listOf(
                                    WireTableUpdate(
                                        tableName = "users",
                                        rows = listOf(
                                            TableUpdateRows.PersistentTable(
                                                inserts = bsatnRowList(encodeUser(bob)),
                                                deletes = bsatnRowList(),
                                            )
                                        ),
                                    )
                                ),
                            )
                        ),
                    ),
                ),
            )
        )

        assertEquals(2, receivedEvents.size)
        // First event is from TransactionUpdate (other client)
        assertIs<Event.Transaction>(receivedEvents[0])
        // Second event is from our ReducerResult
        val reducerEvent = receivedEvents[1]
        assertIs<Event.Reducer<*>>(reducerEvent)
        assertEquals("add_user", reducerEvent.event.reducer)
        assertEquals(Status.Committed, reducerEvent.event.status)
    }

    // -- Disconnect --

    @Test
    fun `disconnect invokes onDisconnect callback`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        var disconnected = false

        val conn = createConnection(fake, backgroundScope, onDisconnect = { disconnected = true })

        conn.disconnect()
        // Give time for cancellation to propagate
        yield()

        assertTrue(disconnected)
    }

    @Test
    fun `server close invokes onDisconnect callback`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        var disconnected = false

        createConnection(fake, backgroundScope, onDisconnect = { disconnected = true })

        fake.closeServer()
        // Give time for flow completion to propagate
        yield()

        assertTrue(disconnected)
    }

    // -- UnsubscribeApplied --

    @Test
    fun `unsubscribe applied removes rows from cache`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val alice = User(1u, "alice")
        val aliceBytes = encodeUser(alice)

        val handle = conn.subscriptionBuilder()
            .subscribe("SELECT * FROM users")

        // Apply subscription with initial data
        fake.sendToClient(
            ServerMessage.SubscribeApplied(
                requestId = 1u,
                querySetId = handle.querySetId,
                rows = QueryRows(
                    tables = listOf(
                        SingleTableRows(
                            table = "users",
                            rows = bsatnRowList(aliceBytes),
                        )
                    )
                ),
            )
        )
        assertEquals(1, conn.cache.getTable<User>("users")!!.count)

        // Unsubscribe
        handle.unsubscribe()

        // Server confirms with dropped rows
        fake.sendToClient(
            ServerMessage.UnsubscribeApplied(
                requestId = 2u,
                querySetId = handle.querySetId,
                rows = QueryRows(
                    tables = listOf(
                        SingleTableRows(
                            table = "users",
                            rows = bsatnRowList(aliceBytes),
                        )
                    )
                ),
            )
        )

        assertEquals(0, conn.cache.getTable<User>("users")!!.count)
        assertTrue(handle.isEnded)

    }

    // -- ProcedureResult --

    @Test
    fun `procedure call returns Returned outcome`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val resultDeferred = launch {
            val outcome = conn.callProcedure("get_count", byteArrayOf())
            assertIs<ProcedureOutcome.Returned>(outcome)
            assertEquals("42", outcome.value.decodeToString())
            assertEquals(Timestamp.UNIX_EPOCH, outcome.timestamp)
        }

        // Capture the outgoing CallProcedure message
        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallProcedure>(outgoing)
        assertEquals("get_count", outgoing.procedure)

        // Server responds
        fake.sendToClient(
            ServerMessage.ProcedureResult(
                requestId = outgoing.requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                totalHostExecutionDuration = TimeDuration(Duration.ZERO),
                status = ProcedureStatus.Returned("42".encodeToByteArray()),
            )
        )

        resultDeferred.join()
    }

    @Test
    fun `procedure call returns InternalError outcome`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val resultDeferred = launch {
            val outcome = conn.callProcedure("bad_proc", byteArrayOf())
            assertIs<ProcedureOutcome.InternalError>(outcome)
            assertEquals("wasm trap", outcome.message)
        }

        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallProcedure>(outgoing)

        fake.sendToClient(
            ServerMessage.ProcedureResult(
                requestId = outgoing.requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                totalHostExecutionDuration = TimeDuration(Duration.ZERO),
                status = ProcedureStatus.InternalError("wasm trap"),
            )
        )

        resultDeferred.join()
    }

    @Test
    fun `procedure result for unknown request ID is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        createConnection(fake, backgroundScope)

        // Send a ProcedureResult without a matching call — should not throw
        fake.sendToClient(
            ServerMessage.ProcedureResult(
                requestId = 9999u,
                timestamp = Timestamp.UNIX_EPOCH,
                totalHostExecutionDuration = TimeDuration(Duration.ZERO),
                status = ProcedureStatus.Returned(byteArrayOf()),
            )
        )

        // If we get here without an exception, the test passes
    }

    @Test
    fun `typed procedure call deserializes return value`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val resultDeferred = launch {
            val count: UInt = conn.callProcedure("get_count", byteArrayOf(), UInt.serializer())
            assertEquals(42u, count)
        }

        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallProcedure>(outgoing)

        fake.sendToClient(
            ServerMessage.ProcedureResult(
                requestId = outgoing.requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                totalHostExecutionDuration = TimeDuration(Duration.ZERO),
                status = ProcedureStatus.Returned(Bsatn.encodeToByteArray(UInt.serializer(), 42u)),
            )
        )

        resultDeferred.join()
    }

    @Test
    fun `typed procedure call throws on server error`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeWebSocketConnection()
        val conn = createConnection(fake, backgroundScope)

        val resultDeferred = launch {
            val error = try {
                conn.callProcedure("bad_proc", byteArrayOf(), UInt.serializer())
                null
            } catch (e: SpacetimeError.Internal) {
                e
            }
            assertNotNull(error)
            assertEquals("wasm trap", error.message)
        }

        val outgoing = fake.outgoing.tryReceive().getOrNull()
        assertNotNull(outgoing)
        assertIs<ClientMessage.CallProcedure>(outgoing)

        fake.sendToClient(
            ServerMessage.ProcedureResult(
                requestId = outgoing.requestId,
                timestamp = Timestamp.UNIX_EPOCH,
                totalHostExecutionDuration = TimeDuration(Duration.ZERO),
                status = ProcedureStatus.InternalError("wasm trap"),
            )
        )

        resultDeferred.join()
    }

    // -- Reconnect --

    @Test
    fun `reconnects after abnormal disconnect`() = runTest(UnconfinedTestDispatcher()) {
        // Use a channel of fake connections so we can supply a fresh one on reconnect
        val fakeConnections = Channel<FakeWebSocketConnection>(Channel.UNLIMITED)
        val fake1 = FakeWebSocketConnection()
        val fake2 = FakeWebSocketConnection()
        fakeConnections.trySend(fake1)
        fakeConnections.trySend(fake2)

        val connectCount = mutableListOf<Int>()
        var connectCounter = 0

        val connection = SpacetimeDbConnection(
            cache = ClientCache(),
            callbacks = DbCallbacks(),
            connector = { _ -> fakeConnections.receive().asWebSocketConnection() },
            scope = backgroundScope,
            onConnect = { _, _, _ -> connectCounter++; connectCount.add(connectCounter) },
            onConnectError = null,
            onDisconnect = null,
            tableDeserializers = mapOf("users" to User.serializer()),
            reconnectConfig = ReconnectConfig(
                maxAttempts = 3,
                initialDelay = Duration.ZERO, // no delay in tests
                maxDelay = Duration.ZERO,
            ),
        )
        connection.start()

        // First connection: handshake
        val identity = Identity(U256(1u, 0u, 0u, 0u))
        val connId = ConnectionId(U128(4u, 3u))
        fake1.sendToClient(ServerMessage.InitialConnection(identity, connId, "token-1"))

        assertEquals(1, connectCount.size)

        // Simulate abnormal disconnect (server closes the channel)
        fake1.closeServer()
        yield()

        // Second connection: handshake
        fake2.sendToClient(ServerMessage.InitialConnection(identity, connId, "token-2"))
        yield()

        assertEquals(2, connectCount.size)
        assertTrue(connection.isActive)
    }

    @Test
    fun `re-subscribes after reconnect`() = runTest(UnconfinedTestDispatcher()) {
        val fakeConnections = Channel<FakeWebSocketConnection>(Channel.UNLIMITED)
        val fake1 = FakeWebSocketConnection()
        val fake2 = FakeWebSocketConnection()
        fakeConnections.trySend(fake1)
        fakeConnections.trySend(fake2)

        val connection = SpacetimeDbConnection(
            cache = ClientCache(),
            callbacks = DbCallbacks(),
            connector = { _ -> fakeConnections.receive().asWebSocketConnection() },
            scope = backgroundScope,
            onConnect = null,
            onConnectError = null,
            onDisconnect = null,
            tableDeserializers = mapOf("users" to User.serializer()),
            reconnectConfig = ReconnectConfig(
                maxAttempts = 3,
                initialDelay = Duration.ZERO,
                maxDelay = Duration.ZERO,
            ),
        )
        connection.start()

        // First connection
        val identity = Identity(U256(1u, 0u, 0u, 0u))
        val connId = ConnectionId(U128(4u, 3u))
        fake1.sendToClient(ServerMessage.InitialConnection(identity, connId, "token"))

        // Subscribe
        connection.subscriptionBuilder()
            .subscribe("SELECT * FROM users")

        // Drain the Subscribe message from fake1's outgoing
        val sub1 = fake1.outgoing.tryReceive().getOrNull()
        assertNotNull(sub1)
        assertIs<ClientMessage.Subscribe>(sub1)

        // Disconnect and reconnect
        fake1.closeServer()
        yield()

        // Second connection
        fake2.sendToClient(ServerMessage.InitialConnection(identity, connId, "token"))

        // Should see a re-subscribe message on the new connection
        val sub2 = fake2.outgoing.tryReceive().getOrNull()
        assertNotNull(sub2)
        assertIs<ClientMessage.Subscribe>(sub2)
        assertEquals(sub1.queryStrings, sub2.queryStrings)
    }

    @Test
    fun `cache is cleared on reconnect`() = runTest(UnconfinedTestDispatcher()) {
        val fakeConnections = Channel<FakeWebSocketConnection>(Channel.UNLIMITED)
        val fake1 = FakeWebSocketConnection()
        val fake2 = FakeWebSocketConnection()
        fakeConnections.trySend(fake1)
        fakeConnections.trySend(fake2)

        val cache = ClientCache()
        val connection = SpacetimeDbConnection(
            cache = cache,
            callbacks = DbCallbacks(),
            connector = { _ -> fakeConnections.receive().asWebSocketConnection() },
            scope = backgroundScope,
            onConnect = null,
            onConnectError = null,
            onDisconnect = null,
            tableDeserializers = mapOf("users" to User.serializer()),
            reconnectConfig = ReconnectConfig(
                maxAttempts = 3,
                initialDelay = Duration.ZERO,
                maxDelay = Duration.ZERO,
            ),
        )
        connection.start()

        // Populate cache
        val identity = Identity(U256(1u, 0u, 0u, 0u))
        val connId = ConnectionId(U128(4u, 3u))
        fake1.sendToClient(ServerMessage.InitialConnection(identity, connId, "token"))

        val handle = connection.subscriptionBuilder()
            .subscribe("SELECT * FROM users")
        fake1.outgoing.tryReceive() // drain

        val alice = User(1u, "alice")
        fake1.sendToClient(
            ServerMessage.SubscribeApplied(
                requestId = 1u,
                querySetId = handle.querySetId,
                rows = QueryRows(
                    tables = listOf(
                        SingleTableRows(table = "users", rows = bsatnRowList(encodeUser(alice)))
                    )
                ),
            )
        )
        assertEquals(1, cache.getTable<User>("users")?.count)

        // Disconnect
        fake1.closeServer()
        yield()

        // Cache should be cleared after disconnect
        assertEquals(0, cache.getTable<User>("users")?.count)
    }

    @Test
    fun `user disconnect does not reconnect`() = runTest(UnconfinedTestDispatcher()) {
        val fakeConnections = Channel<FakeWebSocketConnection>(Channel.UNLIMITED)
        val fake1 = FakeWebSocketConnection()
        fakeConnections.trySend(fake1)

        var disconnected = false
        val connection = SpacetimeDbConnection(
            cache = ClientCache(),
            callbacks = DbCallbacks(),
            connector = { _ -> fakeConnections.receive().asWebSocketConnection() },
            scope = backgroundScope,
            onConnect = null,
            onConnectError = null,
            onDisconnect = { disconnected = true },
            tableDeserializers = mapOf("users" to User.serializer()),
            reconnectConfig = ReconnectConfig(
                maxAttempts = 3,
                initialDelay = Duration.ZERO,
                maxDelay = Duration.ZERO,
            ),
        )
        connection.start()

        val identity = Identity(U256(1u, 0u, 0u, 0u))
        val connId = ConnectionId(U128(4u, 3u))
        fake1.sendToClient(ServerMessage.InitialConnection(identity, connId, "token"))

        // User-initiated disconnect
        connection.disconnect()
        yield()

        assertTrue(disconnected)
        assertFalse(connection.isActive)
    }

    @Test
    fun `gives up after max reconnect attempts`() = runTest(UnconfinedTestDispatcher()) {
        var connectAttempts = 0
        var disconnectError: SpacetimeError? = null

        val connection = SpacetimeDbConnection(
            cache = ClientCache(),
            callbacks = DbCallbacks(),
            connector = { _ ->
                connectAttempts++
                if (connectAttempts == 1) {
                    // First connect succeeds
                    val fake = FakeWebSocketConnection()
                    // Immediately close to trigger reconnect
                    fake.closeServer()
                    fake.asWebSocketConnection()
                } else {
                    // All reconnects fail
                    throw RuntimeException("connection refused")
                }
            },
            scope = backgroundScope,
            onConnect = null,
            onConnectError = null,
            onDisconnect = { disconnectError = it },
            tableDeserializers = emptyMap(),
            reconnectConfig = ReconnectConfig(
                maxAttempts = 2,
                initialDelay = Duration.ZERO,
                maxDelay = Duration.ZERO,
            ),
        )
        connection.start()
        yield()
        yield()
        yield()

        // 1 initial + 2 retries = 3
        assertEquals(3, connectAttempts)
        assertNotNull(disconnectError)
        assertFalse(connection.isActive)
    }
}
