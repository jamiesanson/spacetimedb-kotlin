package dev.sanson.spacetimedb.protocol

import dev.sanson.spacetimedb.ConnectionId
import dev.sanson.spacetimedb.Identity
import dev.sanson.spacetimedb.TimeDuration
import dev.sanson.spacetimedb.Timestamp
import dev.sanson.spacetimedb.bsatn.Bsatn
import dev.sanson.spacetimedb.bsatn.U128
import dev.sanson.spacetimedb.bsatn.U256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.microseconds

class ServerMessageTest {

    // -- BsatnRowList --

    @Test
    fun `BsatnRowList rows with FixedSize hint`() {
        val data = byteArrayOf(1, 2, 3, 4, 5, 6)
        val list = BsatnRowList(
            sizeHint = RowSizeHint.FixedSize(2u),
            rowsData = data,
        )
        val rows = list.rows()
        assertEquals(3, rows.size)
        assertContentEquals(byteArrayOf(1, 2), rows[0])
        assertContentEquals(byteArrayOf(3, 4), rows[1])
        assertContentEquals(byteArrayOf(5, 6), rows[2])
    }

    @Test
    fun `BsatnRowList rows with RowOffsets hint`() {
        val data = byteArrayOf(10, 20, 30, 40, 50)
        val list = BsatnRowList(
            sizeHint = RowSizeHint.RowOffsets(listOf(0uL, 2uL, 3uL)),
            rowsData = data,
        )
        val rows = list.rows()
        assertEquals(3, rows.size)
        assertContentEquals(byteArrayOf(10, 20), rows[0])
        assertContentEquals(byteArrayOf(30), rows[1])
        assertContentEquals(byteArrayOf(40, 50), rows[2])
    }

    @Test
    fun `BsatnRowList with zero-size hint returns empty`() {
        val list = BsatnRowList(
            sizeHint = RowSizeHint.FixedSize(0u),
            rowsData = byteArrayOf(1, 2, 3),
        )
        assertTrue(list.rows().isEmpty())
    }

    @Test
    fun `BsatnRowList round-trips through BSATN`() {
        val list = BsatnRowList(
            sizeHint = RowSizeHint.FixedSize(4u),
            rowsData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
        )
        val bytes = Bsatn.encodeToByteArray(BsatnRowList.serializer(), list)
        val decoded = Bsatn.decodeFromByteArray(BsatnRowList.serializer(), bytes)
        assertEquals(list.sizeHint, decoded.sizeHint)
        assertContentEquals(list.rowsData, decoded.rowsData)
    }

    @Test
    fun `BsatnRowList with RowOffsets round-trips through BSATN`() {
        val list = BsatnRowList(
            sizeHint = RowSizeHint.RowOffsets(listOf(0uL, 3uL, 5uL)),
            rowsData = byteArrayOf(1, 2, 3, 4, 5, 6, 7),
        )
        val bytes = Bsatn.encodeToByteArray(BsatnRowList.serializer(), list)
        val decoded = Bsatn.decodeFromByteArray(BsatnRowList.serializer(), bytes)
        assertTrue(decoded.sizeHint is RowSizeHint.RowOffsets)
        assertEquals(
            listOf(0uL, 3uL, 5uL),
            (decoded.sizeHint as RowSizeHint.RowOffsets).offsets,
        )
        assertContentEquals(list.rowsData, decoded.rowsData)
    }

    // -- RowSizeHint tags --

    @Test
    fun `RowSizeHint FixedSize has tag 0`() {
        val hint: RowSizeHint = RowSizeHint.FixedSize(8u)
        val bytes = Bsatn.encodeToByteArray(RowSizeHint.serializer(), hint)
        assertEquals(0, bytes[0].toInt())
    }

    @Test
    fun `RowSizeHint RowOffsets has tag 1`() {
        val hint: RowSizeHint = RowSizeHint.RowOffsets(emptyList())
        val bytes = Bsatn.encodeToByteArray(RowSizeHint.serializer(), hint)
        assertEquals(1, bytes[0].toInt())
    }

    // -- TableUpdateRows --

    @Test
    fun `PersistentTable round-trips through BSATN`() {
        val inserts = BsatnRowList(RowSizeHint.FixedSize(2u), byteArrayOf(1, 2))
        val deletes = BsatnRowList(RowSizeHint.FixedSize(2u), byteArrayOf(3, 4))
        val rows: TableUpdateRows = TableUpdateRows.PersistentTable(inserts, deletes)

        val bytes = Bsatn.encodeToByteArray(TableUpdateRows.serializer(), rows)
        val decoded = Bsatn.decodeFromByteArray(TableUpdateRows.serializer(), bytes)

        assertTrue(decoded is TableUpdateRows.PersistentTable)
        assertContentEquals(byteArrayOf(1, 2), decoded.inserts.rowsData)
        assertContentEquals(byteArrayOf(3, 4), decoded.deletes.rowsData)
    }

    @Test
    fun `EventTable round-trips through BSATN`() {
        val events = BsatnRowList(RowSizeHint.FixedSize(3u), byteArrayOf(5, 6, 7))
        val rows: TableUpdateRows = TableUpdateRows.EventTable(events)

        val bytes = Bsatn.encodeToByteArray(TableUpdateRows.serializer(), rows)
        val decoded = Bsatn.decodeFromByteArray(TableUpdateRows.serializer(), bytes)

        assertTrue(decoded is TableUpdateRows.EventTable)
        assertContentEquals(byteArrayOf(5, 6, 7), decoded.events.rowsData)
    }

    @Test
    fun `PersistentTable has tag 0 and EventTable has tag 1`() {
        val pt: TableUpdateRows = TableUpdateRows.PersistentTable(
            BsatnRowList(RowSizeHint.FixedSize(0u), byteArrayOf()),
            BsatnRowList(RowSizeHint.FixedSize(0u), byteArrayOf()),
        )
        val et: TableUpdateRows = TableUpdateRows.EventTable(
            BsatnRowList(RowSizeHint.FixedSize(0u), byteArrayOf()),
        )
        assertEquals(0, Bsatn.encodeToByteArray(TableUpdateRows.serializer(), pt)[0].toInt())
        assertEquals(1, Bsatn.encodeToByteArray(TableUpdateRows.serializer(), et)[0].toInt())
    }

    // -- ServerMessage tags --

    @Test
    fun `InitialConnection has tag 0`() {
        val msg: ServerMessage = ServerMessage.InitialConnection(
            identity = Identity(U256.ZERO),
            connectionId = ConnectionId(U128.ZERO),
            token = "",
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        assertEquals(0, bytes[0].toInt())
    }

    @Test
    fun `SubscribeApplied has tag 1`() {
        val msg: ServerMessage = ServerMessage.SubscribeApplied(
            requestId = 0u,
            querySetId = QuerySetId(0u),
            rows = QueryRows(emptyList()),
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        assertEquals(1, bytes[0].toInt())
    }

    @Test
    fun `UnsubscribeApplied has tag 2`() {
        val msg: ServerMessage = ServerMessage.UnsubscribeApplied(
            requestId = 0u,
            querySetId = QuerySetId(0u),
            rows = null,
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        assertEquals(2, bytes[0].toInt())
    }

    @Test
    fun `SubscriptionError has tag 3`() {
        val msg: ServerMessage = ServerMessage.SubscriptionError(
            requestId = null,
            querySetId = QuerySetId(0u),
            error = "",
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        assertEquals(3, bytes[0].toInt())
    }

    @Test
    fun `TransactionUpdateMsg has tag 4`() {
        val msg: ServerMessage = ServerMessage.TransactionUpdateMsg(
            update = TransactionUpdate(emptyList()),
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        assertEquals(4, bytes[0].toInt())
    }

    @Test
    fun `OneOffQueryResult has tag 5`() {
        val msg: ServerMessage = ServerMessage.OneOffQueryResult(
            requestId = 0u,
            result = QueryResult.Ok(QueryRows(emptyList())),
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        assertEquals(5, bytes[0].toInt())
    }

    @Test
    fun `ReducerResult has tag 6`() {
        val msg: ServerMessage = ServerMessage.ReducerResult(
            requestId = 0u,
            timestamp = Timestamp.UNIX_EPOCH,
            result = ReducerOutcome.OkEmpty,
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        assertEquals(6, bytes[0].toInt())
    }

    @Test
    fun `ProcedureResult has tag 7`() {
        val msg: ServerMessage = ServerMessage.ProcedureResult(
            status = ProcedureStatus.Returned(byteArrayOf()),
            timestamp = Timestamp.UNIX_EPOCH,
            totalHostExecutionDuration = TimeDuration.ZERO,
            requestId = 0u,
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        assertEquals(7, bytes[0].toInt())
    }

    // -- ServerMessage round-trips --

    @Test
    fun `InitialConnection round-trips through BSATN`() {
        val identity = Identity(U256(1uL, 2uL, 3uL, 4uL))
        val connectionId = ConnectionId(U128(5uL, 6uL))
        val msg: ServerMessage = ServerMessage.InitialConnection(
            identity = identity,
            connectionId = connectionId,
            token = "my-auth-token",
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.InitialConnection)
        assertEquals(identity, decoded.identity)
        assertEquals(connectionId, decoded.connectionId)
        assertEquals("my-auth-token", decoded.token)
    }

    @Test
    fun `SubscribeApplied round-trips with table data`() {
        val rowList = BsatnRowList(RowSizeHint.FixedSize(4u), byteArrayOf(1, 0, 0, 0))
        val msg: ServerMessage = ServerMessage.SubscribeApplied(
            requestId = 42u,
            querySetId = QuerySetId(7u),
            rows = QueryRows(listOf(SingleTableRows("users", rowList))),
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.SubscribeApplied)
        assertEquals(42u, decoded.requestId)
        assertEquals(QuerySetId(7u), decoded.querySetId)
        assertEquals(1, decoded.rows.tables.size)
        assertEquals("users", decoded.rows.tables[0].table)
        assertContentEquals(byteArrayOf(1, 0, 0, 0), decoded.rows.tables[0].rows.rowsData)
    }

    @Test
    fun `UnsubscribeApplied with null rows round-trips`() {
        val msg: ServerMessage = ServerMessage.UnsubscribeApplied(
            requestId = 10u,
            querySetId = QuerySetId(3u),
            rows = null,
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.UnsubscribeApplied)
        assertEquals(10u, decoded.requestId)
        assertNull(decoded.rows)
    }

    @Test
    fun `UnsubscribeApplied with non-null rows round-trips`() {
        val msg: ServerMessage = ServerMessage.UnsubscribeApplied(
            requestId = 10u,
            querySetId = QuerySetId(3u),
            rows = QueryRows(emptyList()),
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.UnsubscribeApplied)
        assertEquals(10u, decoded.requestId)
        assertEquals(0, decoded.rows!!.tables.size)
    }

    @Test
    fun `SubscriptionError with null requestId round-trips`() {
        val msg: ServerMessage = ServerMessage.SubscriptionError(
            requestId = null,
            querySetId = QuerySetId(1u),
            error = "bad query",
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.SubscriptionError)
        assertNull(decoded.requestId)
        assertEquals("bad query", decoded.error)
    }

    @Test
    fun `SubscriptionError with non-null requestId round-trips`() {
        val msg: ServerMessage = ServerMessage.SubscriptionError(
            requestId = 99u,
            querySetId = QuerySetId(1u),
            error = "parse error",
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.SubscriptionError)
        assertEquals(99u, decoded.requestId)
    }

    @Test
    fun `TransactionUpdateMsg round-trips with query sets`() {
        val rowList = BsatnRowList(RowSizeHint.FixedSize(1u), byteArrayOf(42))
        val update = TransactionUpdate(
            querySets = listOf(
                QuerySetUpdate(
                    querySetId = QuerySetId(1u),
                    tables = listOf(
                        TableUpdate(
                            tableName = "players",
                            rows = listOf(
                                TableUpdateRows.PersistentTable(
                                    inserts = rowList,
                                    deletes = BsatnRowList(RowSizeHint.FixedSize(0u), byteArrayOf()),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val msg: ServerMessage = ServerMessage.TransactionUpdateMsg(update = update)
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.TransactionUpdateMsg)
        val qs = decoded.update.querySets
        assertEquals(1, qs.size)
        assertEquals(QuerySetId(1u), qs[0].querySetId)
        assertEquals("players", qs[0].tables[0].tableName)
    }

    @Test
    fun `OneOffQueryResult Ok round-trips`() {
        val msg: ServerMessage = ServerMessage.OneOffQueryResult(
            requestId = 5u,
            result = QueryResult.Ok(QueryRows(emptyList())),
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.OneOffQueryResult)
        assertTrue(decoded.result is QueryResult.Ok)
    }

    @Test
    fun `OneOffQueryResult Err round-trips`() {
        val msg: ServerMessage = ServerMessage.OneOffQueryResult(
            requestId = 5u,
            result = QueryResult.Err("query failed"),
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.OneOffQueryResult)
        assertTrue(decoded.result is QueryResult.Err)
        assertEquals("query failed", (decoded.result as QueryResult.Err).error)
    }

    // -- ReducerOutcome variants --

    @Test
    fun `ReducerResult with Ok outcome round-trips`() {
        val ts = Timestamp.fromEpochMicroseconds(1_000_000)
        val msg: ServerMessage = ServerMessage.ReducerResult(
            requestId = 8u,
            timestamp = ts,
            result = ReducerOutcome.Ok(
                retValue = byteArrayOf(0x01),
                transactionUpdate = TransactionUpdate(emptyList()),
            ),
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.ReducerResult)
        assertEquals(8u, decoded.requestId)
        assertEquals(ts, decoded.timestamp)
        assertTrue(decoded.result is ReducerOutcome.Ok)
        assertContentEquals(byteArrayOf(0x01), (decoded.result as ReducerOutcome.Ok).retValue)
    }

    @Test
    fun `ReducerResult with OkEmpty outcome round-trips`() {
        val msg: ServerMessage = ServerMessage.ReducerResult(
            requestId = 9u,
            timestamp = Timestamp.UNIX_EPOCH,
            result = ReducerOutcome.OkEmpty,
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.ReducerResult)
        assertTrue(decoded.result is ReducerOutcome.OkEmpty)
    }

    @Test
    fun `ReducerResult with Err outcome round-trips`() {
        val errBytes = byteArrayOf(0xFF.toByte(), 0x01)
        val msg: ServerMessage = ServerMessage.ReducerResult(
            requestId = 10u,
            timestamp = Timestamp.UNIX_EPOCH,
            result = ReducerOutcome.Err(errBytes),
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.ReducerResult)
        assertTrue(decoded.result is ReducerOutcome.Err)
        assertContentEquals(errBytes, (decoded.result as ReducerOutcome.Err).error)
    }

    @Test
    fun `ReducerResult with InternalError round-trips`() {
        val msg: ServerMessage = ServerMessage.ReducerResult(
            requestId = 11u,
            timestamp = Timestamp.UNIX_EPOCH,
            result = ReducerOutcome.InternalError("something broke"),
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.ReducerResult)
        assertTrue(decoded.result is ReducerOutcome.InternalError)
        assertEquals("something broke", (decoded.result as ReducerOutcome.InternalError).message)
    }

    // -- ReducerOutcome tags --

    @Test
    fun `ReducerOutcome Ok has tag 0 and OkEmpty has tag 1 and Err has tag 2 and InternalError has tag 3`() {
        val ok: ReducerOutcome = ReducerOutcome.Ok(
            retValue = byteArrayOf(),
            transactionUpdate = TransactionUpdate(emptyList()),
        )
        val okEmpty: ReducerOutcome = ReducerOutcome.OkEmpty
        val err: ReducerOutcome = ReducerOutcome.Err(byteArrayOf())
        val internal: ReducerOutcome = ReducerOutcome.InternalError("")

        assertEquals(0, Bsatn.encodeToByteArray(ReducerOutcome.serializer(), ok)[0].toInt())
        assertEquals(1, Bsatn.encodeToByteArray(ReducerOutcome.serializer(), okEmpty)[0].toInt())
        assertEquals(2, Bsatn.encodeToByteArray(ReducerOutcome.serializer(), err)[0].toInt())
        assertEquals(3, Bsatn.encodeToByteArray(ReducerOutcome.serializer(), internal)[0].toInt())
    }

    // -- ProcedureResult --

    @Test
    fun `ProcedureResult round-trips through BSATN`() {
        val msg: ServerMessage = ServerMessage.ProcedureResult(
            status = ProcedureStatus.Returned(byteArrayOf(0x42)),
            timestamp = Timestamp.fromEpochMicroseconds(5_000_000),
            totalHostExecutionDuration = TimeDuration(100.microseconds),
            requestId = 20u,
        )
        val bytes = Bsatn.encodeToByteArray(ServerMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ServerMessage.serializer(), bytes)

        assertTrue(decoded is ServerMessage.ProcedureResult)
        assertEquals(20u, decoded.requestId)
        assertTrue(decoded.status is ProcedureStatus.Returned)
        assertContentEquals(
            byteArrayOf(0x42),
            (decoded.status as ProcedureStatus.Returned).value,
        )
    }

    // -- QueryResult tags --

    @Test
    fun `QueryResult Ok has tag 0 and Err has tag 1`() {
        val ok: QueryResult = QueryResult.Ok(QueryRows(emptyList()))
        val err: QueryResult = QueryResult.Err("fail")

        assertEquals(0, Bsatn.encodeToByteArray(QueryResult.serializer(), ok)[0].toInt())
        assertEquals(1, Bsatn.encodeToByteArray(QueryResult.serializer(), err)[0].toInt())
    }
}
