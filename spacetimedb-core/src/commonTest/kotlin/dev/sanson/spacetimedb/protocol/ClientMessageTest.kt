package dev.sanson.spacetimedb.protocol

import dev.sanson.spacetimedb.bsatn.Bsatn
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientMessageTest {

    // -- QuerySetId --

    @Test
    fun `QuerySetId round-trips through BSATN`() {
        val id = QuerySetId(42u)
        val bytes = Bsatn.encodeToByteArray(QuerySetId.serializer(), id)
        assertEquals(4, bytes.size) // u32 = 4 bytes
        val decoded = Bsatn.decodeFromByteArray(QuerySetId.serializer(), bytes)
        assertEquals(id, decoded)
    }

    // -- Flag types --

    @Test
    fun `CallReducerFlags round-trips as single byte`() {
        val flags = CallReducerFlags.Default
        val bytes = Bsatn.encodeToByteArray(CallReducerFlags.serializer(), flags)
        assertEquals(1, bytes.size) // u8
        assertEquals(0, bytes[0].toInt())
        val decoded = Bsatn.decodeFromByteArray(CallReducerFlags.serializer(), bytes)
        assertEquals(flags, decoded)
    }

    @Test
    fun `UnsubscribeFlags round-trips`() {
        val flags = UnsubscribeFlags.SendDroppedRows
        val bytes = Bsatn.encodeToByteArray(UnsubscribeFlags.serializer(), flags)
        assertEquals(1, bytes.size)
        assertEquals(1, bytes[0].toInt())
        val decoded = Bsatn.decodeFromByteArray(UnsubscribeFlags.serializer(), bytes)
        assertEquals(flags, decoded)
    }

    // -- Subscribe --

    @Test
    fun `Subscribe round-trips through BSATN`() {
        val msg: ClientMessage = ClientMessage.Subscribe(
            requestId = 1u,
            querySetId = QuerySetId(10u),
            queryStrings = listOf("SELECT * FROM users", "SELECT * FROM items"),
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ClientMessage.serializer(), bytes)

        assertTrue(decoded is ClientMessage.Subscribe)
        assertEquals(1u, decoded.requestId)
        assertEquals(QuerySetId(10u), decoded.querySetId)
        assertEquals(listOf("SELECT * FROM users", "SELECT * FROM items"), decoded.queryStrings)
    }

    @Test
    fun `Subscribe has tag 0`() {
        val msg: ClientMessage = ClientMessage.Subscribe(
            requestId = 0u,
            querySetId = QuerySetId(0u),
            queryStrings = emptyList(),
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)
        assertEquals(0, bytes[0].toInt()) // tag byte
    }

    // -- Unsubscribe --

    @Test
    fun `Unsubscribe round-trips through BSATN`() {
        val msg: ClientMessage = ClientMessage.Unsubscribe(
            requestId = 2u,
            querySetId = QuerySetId(20u),
            flags = UnsubscribeFlags.SendDroppedRows,
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ClientMessage.serializer(), bytes)

        assertTrue(decoded is ClientMessage.Unsubscribe)
        assertEquals(2u, decoded.requestId)
        assertEquals(UnsubscribeFlags.SendDroppedRows, decoded.flags)
    }

    @Test
    fun `Unsubscribe has tag 1`() {
        val msg: ClientMessage = ClientMessage.Unsubscribe(
            requestId = 0u,
            querySetId = QuerySetId(0u),
            flags = UnsubscribeFlags.Default,
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)
        assertEquals(1, bytes[0].toInt())
    }

    // -- OneOffQuery --

    @Test
    fun `OneOffQuery round-trips through BSATN`() {
        val msg: ClientMessage = ClientMessage.OneOffQuery(
            requestId = 3u,
            queryString = "SELECT * FROM users WHERE id = 1",
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ClientMessage.serializer(), bytes)

        assertTrue(decoded is ClientMessage.OneOffQuery)
        assertEquals(3u, decoded.requestId)
        assertEquals("SELECT * FROM users WHERE id = 1", decoded.queryString)
    }

    @Test
    fun `OneOffQuery has tag 2`() {
        val msg: ClientMessage = ClientMessage.OneOffQuery(
            requestId = 0u,
            queryString = "",
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)
        assertEquals(2, bytes[0].toInt())
    }

    // -- CallReducer --

    @Test
    fun `CallReducer round-trips through BSATN`() {
        val args = byteArrayOf(0x01, 0x02, 0x03)
        val msg: ClientMessage = ClientMessage.CallReducer(
            requestId = 4u,
            flags = CallReducerFlags.Default,
            reducer = "add_user",
            args = args,
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ClientMessage.serializer(), bytes)

        assertTrue(decoded is ClientMessage.CallReducer)
        assertEquals(4u, decoded.requestId)
        assertEquals(CallReducerFlags.Default, decoded.flags)
        assertEquals("add_user", decoded.reducer)
        assertContentEquals(args, decoded.args)
    }

    @Test
    fun `CallReducer has tag 3`() {
        val msg: ClientMessage = ClientMessage.CallReducer(
            requestId = 0u,
            flags = CallReducerFlags.Default,
            reducer = "",
            args = byteArrayOf(),
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)
        assertEquals(3, bytes[0].toInt())
    }

    // -- CallProcedure --

    @Test
    fun `CallProcedure round-trips through BSATN`() {
        val args = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val msg: ClientMessage = ClientMessage.CallProcedure(
            requestId = 5u,
            flags = CallProcedureFlags.Default,
            procedure = "my_proc",
            args = args,
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)
        val decoded = Bsatn.decodeFromByteArray(ClientMessage.serializer(), bytes)

        assertTrue(decoded is ClientMessage.CallProcedure)
        assertEquals(5u, decoded.requestId)
        assertEquals("my_proc", decoded.procedure)
        assertContentEquals(args, decoded.args)
    }

    @Test
    fun `CallProcedure has tag 4`() {
        val msg: ClientMessage = ClientMessage.CallProcedure(
            requestId = 0u,
            flags = CallProcedureFlags.Default,
            procedure = "",
            args = byteArrayOf(),
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)
        assertEquals(4, bytes[0].toInt())
    }

    // -- Wire format verification --

    @Test
    fun `Subscribe encodes fields in correct order`() {
        val msg: ClientMessage = ClientMessage.Subscribe(
            requestId = 1u,
            querySetId = QuerySetId(2u),
            queryStrings = listOf("q1"),
        )
        val bytes = Bsatn.encodeToByteArray(ClientMessage.serializer(), msg)

        // tag(0) + requestId(u32LE) + querySetId(u32LE) + queryStrings(u32LE count + string)
        assertEquals(0, bytes[0].toInt()) // tag = Subscribe
        // requestId = 1 at bytes 1..4
        assertEquals(1, bytes[1].toInt())
        assertEquals(0, bytes[2].toInt())
        assertEquals(0, bytes[3].toInt())
        assertEquals(0, bytes[4].toInt())
        // querySetId = 2 at bytes 5..8
        assertEquals(2, bytes[5].toInt())
        assertEquals(0, bytes[6].toInt())
    }
}
