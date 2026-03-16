package dev.sanson.spacetimedb.transport

import java.io.ByteArrayInputStream
import okio.Buffer
import okio.GzipSource
import okio.buffer
import org.brotli.dec.BrotliInputStream

internal actual fun decompressGzip(data: ByteArray): ByteArray {
    return GzipSource(Buffer().write(data)).buffer().readByteArray()
}

internal actual fun decompressBrotli(data: ByteArray): ByteArray {
    return BrotliInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
