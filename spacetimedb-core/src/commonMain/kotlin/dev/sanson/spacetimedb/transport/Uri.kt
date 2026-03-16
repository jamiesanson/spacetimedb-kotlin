package dev.sanson.spacetimedb.transport

import dev.sanson.spacetimedb.ConnectionId
import dev.sanson.spacetimedb.protocol.Compression
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.appendPathSegments

/**
 * Builds the WebSocket URI for connecting to a SpacetimeDB instance.
 *
 * Converts HTTP(S) schemes to WS(S) and appends the subscribe path with query parameters for
 * compression, connection ID, and confirmed reads.
 *
 * @param host Base URI of the SpacetimeDB instance (e.g. "http://localhost:3000")
 * @param databaseName Name of the database to connect to
 * @param compression Compression preference for server messages
 * @param connectionId Optional connection ID for reconnection
 * @param confirmed Optional flag to enable/disable confirmed reads
 */
internal fun buildSpacetimeUri(
    host: String,
    databaseName: String,
    compression: Compression,
    connectionId: ConnectionId? = null,
    confirmed: Boolean? = null,
): String {
    val base = Url(host)

    return URLBuilder(base)
        .apply {
            protocol =
                when (base.protocol) {
                    URLProtocol.HTTP -> URLProtocol("ws", 80)
                    URLProtocol.HTTPS -> URLProtocol("wss", 443)
                    else -> base.protocol
                }

            appendPathSegments("v1", "database", databaseName, "subscribe")

            parameters.append("compression", compression.queryParam)

            if (connectionId != null) {
                parameters.append("connection_id", connectionId.toHex())
            }

            if (confirmed != null) {
                parameters.append("confirmed", confirmed.toString())
            }
        }
        .buildString()
}
