package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.bsatn.Bsatn
import dev.sanson.spacetimedb.bsatn.U128
import dev.sanson.spacetimedb.bsatn.U256
import dev.sanson.spacetimedb.protocol.BsatnRowList
import dev.sanson.spacetimedb.protocol.ClientMessage
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    ): SpacetimeDbConnection {
        val connection = SpacetimeDbConnection(
            cache = ClientCache(),
            callbacks = DbCallbacks(),
            connection = fake.asWebSocketConnection(),
            scope = scope,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            tableDeserializers = mapOf("users" to User.serializer()),
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
}
