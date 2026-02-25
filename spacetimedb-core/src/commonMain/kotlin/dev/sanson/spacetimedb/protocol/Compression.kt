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
public enum class Compression(
    internal val tag: Byte,
    internal val queryParam: String,
) {
    None(tag = 0, queryParam = "None"),
    Brotli(tag = 1, queryParam = "Brotli"),
    Gzip(tag = 2, queryParam = "Gzip"),
    ;

    public companion object {
        internal fun fromTag(tag: Byte): Compression =
            entries.firstOrNull { it.tag == tag }
                ?: throw IllegalArgumentException("Unknown compression tag: $tag")
    }
}
