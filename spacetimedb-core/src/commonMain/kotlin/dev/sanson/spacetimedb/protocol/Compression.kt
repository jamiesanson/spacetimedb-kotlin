package dev.sanson.spacetimedb.protocol

/**
 * Compression algorithm preference for server-to-client messages.
 *
 * The client specifies its preferred compression via a query parameter when
 * connecting. The server then compresses responses accordingly.
 *
 * Each server message is framed as `[tag_byte][payload]`:
 * - Tag 0: no compression
 * - Tag 1: Brotli
 * - Tag 2: Gzip
 */
public abstract class Compression private constructor(
    internal val tag: Byte,
    internal val queryParam: String,
) {
    public data object None : Compression(tag = 0, queryParam = "None")
    public data object Brotli : Compression(tag = 1, queryParam = "Brotli")
    public data object Gzip : Compression(tag = 2, queryParam = "Gzip")

    internal companion object {
        fun fromTag(tag: Byte): Compression = when (tag.toInt()) {
            0 -> None
            1 -> Brotli
            2 -> Gzip
            else -> throw IllegalArgumentException("Unknown compression tag: $tag")
        }
    }
}
