package dev.sanson.spacetimedb.transport

import dev.sanson.spacetimedb.protocol.Compression
import okio.Buffer
import okio.GzipSink
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals

class GzipDecompressionJvmTest {
    private fun gzipCompress(data: ByteArray): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.write(data) }
        return buffer.readByteArray()
    }

    @Test
    fun `gzip decompression round-trips`() {
        val original = "Hello, SpacetimeDB!".encodeToByteArray()
        val compressed = gzipCompress(original)
        assertContentEquals(original, decompressGzip(compressed))
    }

    @Test
    fun `gzip tag with compressed payload decompresses correctly`() {
        val original = byteArrayOf(10, 20, 30, 40, 50)
        val compressed = gzipCompress(original)
        val raw = byteArrayOf(Compression.Gzip.tag) + compressed
        assertContentEquals(original, decompressServerMessage(raw))
    }

    @Test
    fun `large payload decompresses correctly`() {
        val original = ByteArray(10_000) { (it % 256).toByte() }
        val compressed = gzipCompress(original)
        assertContentEquals(original, decompressGzip(compressed))
    }
}
