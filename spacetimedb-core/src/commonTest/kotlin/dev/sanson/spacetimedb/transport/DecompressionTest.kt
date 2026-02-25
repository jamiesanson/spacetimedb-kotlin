package dev.sanson.spacetimedb.transport

import dev.sanson.spacetimedb.protocol.Compression
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class DecompressionTest {
    @Test
    fun `empty message throws`() {
        assertFailsWith<IllegalArgumentException> {
            decompressServerMessage(byteArrayOf())
        }
    }

    @Test
    fun `tag 0 returns uncompressed payload`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val raw = byteArrayOf(Compression.None.tag) + payload
        assertContentEquals(payload, decompressServerMessage(raw))
    }

    @Test
    fun `tag 0 with empty payload returns empty array`() {
        val raw = byteArrayOf(Compression.None.tag)
        assertContentEquals(byteArrayOf(), decompressServerMessage(raw))
    }

    @Test
    fun `unknown tag throws`() {
        val raw = byteArrayOf(99, 1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            decompressServerMessage(raw)
        }
    }

    @Test
    fun `brotli tag delegates to decompressBrotli`() {
        // On platforms without Brotli support, this should throw UnsupportedOperationException
        val raw = byteArrayOf(Compression.Brotli.tag, 1, 2, 3)
        assertFailsWith<UnsupportedOperationException> {
            decompressServerMessage(raw)
        }
    }
}
