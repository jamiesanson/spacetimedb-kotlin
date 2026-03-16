package dev.sanson.spacetimedb.transport

import dev.sanson.spacetimedb.protocol.Compression

/**
 * Decompresses a raw server message by reading the compression tag byte and delegating to the
 * appropriate decompressor.
 *
 * Server messages are framed as `[tag_byte][payload]` where the tag identifies the compression
 * scheme.
 *
 * @throws IllegalArgumentException if the message is empty
 * @throws IllegalArgumentException if the compression tag is unknown
 */
internal fun decompressServerMessage(raw: ByteArray): ByteArray {
    require(raw.isNotEmpty()) { "Empty server message" }

    val tag = raw[0]
    val payload = raw.copyOfRange(1, raw.size)

    return when (val compression = Compression.fromTag(tag)) {
        Compression.None -> payload
        Compression.Brotli -> decompressBrotli(payload)
        Compression.Gzip -> decompressGzip(payload)
        else -> throw IllegalArgumentException("Unhandled compression: $compression")
    }
}

internal expect fun decompressGzip(data: ByteArray): ByteArray

internal expect fun decompressBrotli(data: ByteArray): ByteArray
