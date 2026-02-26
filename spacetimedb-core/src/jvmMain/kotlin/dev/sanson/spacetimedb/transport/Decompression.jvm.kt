package dev.sanson.spacetimedb.transport

import okio.Buffer
import okio.GzipSource
import okio.buffer
import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream

internal actual fun decompressGzip(data: ByteArray): ByteArray {
    return GzipSource(Buffer().write(data)).buffer().readByteArray()
}

internal actual fun decompressBrotli(data: ByteArray): ByteArray {
    return BrotliInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
