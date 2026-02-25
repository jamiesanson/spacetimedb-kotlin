package dev.sanson.spacetimedb.transport

import okio.Buffer
import okio.GzipSource
import okio.buffer

internal actual fun decompressGzip(data: ByteArray): ByteArray {
    return GzipSource(Buffer().write(data)).buffer().readByteArray()
}

internal actual fun decompressBrotli(data: ByteArray): ByteArray {
    throw UnsupportedOperationException("Brotli decompression is not yet supported. Use Compression.None or Compression.Gzip.")
}
