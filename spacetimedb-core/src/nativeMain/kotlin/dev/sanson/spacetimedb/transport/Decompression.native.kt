package dev.sanson.spacetimedb.transport

internal actual fun decompressGzip(data: ByteArray): ByteArray {
    throw UnsupportedOperationException("Gzip decompression is not yet supported on Native. Use Compression.None.")
}

internal actual fun decompressBrotli(data: ByteArray): ByteArray {
    throw UnsupportedOperationException("Brotli decompression is not yet supported on Native. Use Compression.None.")
}
