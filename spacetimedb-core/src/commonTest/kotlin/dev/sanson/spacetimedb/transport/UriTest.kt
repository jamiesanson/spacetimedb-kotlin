package dev.sanson.spacetimedb.transport

import dev.sanson.spacetimedb.ConnectionId
import dev.sanson.spacetimedb.bsatn.U128
import dev.sanson.spacetimedb.protocol.Compression
import kotlin.test.Test
import kotlin.test.assertEquals

class UriTest {
    @Test
    fun `http scheme converts to ws`() {
        val uri = buildSpacetimeUri(
            host = "http://localhost:3000",
            databaseName = "my_db",
            compression = Compression.None,
        )
        assertEquals("ws://localhost:3000/v1/database/my_db/subscribe?compression=None", uri)
    }

    @Test
    fun `https scheme converts to wss`() {
        val uri = buildSpacetimeUri(
            host = "https://spacetimedb.com",
            databaseName = "my_db",
            compression = Compression.None,
        )
        assertEquals("wss://spacetimedb.com/v1/database/my_db/subscribe?compression=None", uri)
    }

    @Test
    fun `compression parameter is included`() {
        val brotli = buildSpacetimeUri(
            host = "http://localhost:3000",
            databaseName = "test",
            compression = Compression.Brotli,
        )
        assertEquals("ws://localhost:3000/v1/database/test/subscribe?compression=Brotli", brotli)

        val gzip = buildSpacetimeUri(
            host = "http://localhost:3000",
            databaseName = "test",
            compression = Compression.Gzip,
        )
        assertEquals("ws://localhost:3000/v1/database/test/subscribe?compression=Gzip", gzip)
    }

    @Test
    fun `connection ID is appended as hex`() {
        val connectionId = ConnectionId(U128(lo = 1uL, hi = 0uL))
        val uri = buildSpacetimeUri(
            host = "http://localhost:3000",
            databaseName = "test",
            compression = Compression.None,
            connectionId = connectionId,
        )
        assertEquals(
            "ws://localhost:3000/v1/database/test/subscribe?compression=None&connection_id=00000000000000000000000000000001",
            uri,
        )
    }

    @Test
    fun `confirmed parameter is appended`() {
        val uri = buildSpacetimeUri(
            host = "http://localhost:3000",
            databaseName = "test",
            compression = Compression.None,
            confirmed = true,
        )
        assertEquals(
            "ws://localhost:3000/v1/database/test/subscribe?compression=None&confirmed=true",
            uri,
        )
    }

    @Test
    fun `all parameters combined`() {
        val connectionId = ConnectionId(U128(lo = 0xABCDuL, hi = 0x1234uL))
        val uri = buildSpacetimeUri(
            host = "https://example.com",
            databaseName = "game_db",
            compression = Compression.Brotli,
            connectionId = connectionId,
            confirmed = false,
        )
        assertEquals(
            "wss://example.com/v1/database/game_db/subscribe?compression=Brotli&connection_id=0000000000001234000000000000abcd&confirmed=false",
            uri,
        )
    }

    @Test
    fun `host with trailing slash is handled`() {
        val uri = buildSpacetimeUri(
            host = "http://localhost:3000/",
            databaseName = "test",
            compression = Compression.None,
        )
        assertEquals("ws://localhost:3000/v1/database/test/subscribe?compression=None", uri)
    }

    @Test
    fun `host with existing path is preserved`() {
        val uri = buildSpacetimeUri(
            host = "http://localhost:3000/prefix",
            databaseName = "test",
            compression = Compression.None,
        )
        assertEquals(
            "ws://localhost:3000/prefix/v1/database/test/subscribe?compression=None",
            uri,
        )
    }

    @Test
    fun `host with port is preserved`() {
        val uri = buildSpacetimeUri(
            host = "http://localhost:8080",
            databaseName = "test",
            compression = Compression.None,
        )
        assertEquals("ws://localhost:8080/v1/database/test/subscribe?compression=None", uri)
    }
}
