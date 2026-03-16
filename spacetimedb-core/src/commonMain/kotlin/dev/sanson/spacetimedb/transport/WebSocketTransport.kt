package dev.sanson.spacetimedb.transport

import dev.sanson.spacetimedb.ConnectionId
import dev.sanson.spacetimedb.bsatn.Bsatn
import dev.sanson.spacetimedb.protocol.ClientMessage
import dev.sanson.spacetimedb.protocol.ClientMessageSerializer
import dev.sanson.spacetimedb.protocol.Compression
import dev.sanson.spacetimedb.protocol.ServerMessage
import dev.sanson.spacetimedb.protocol.ServerMessageSerializer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow

/** WebSocket subprotocol identifier for SpacetimeDB v2 binary protocol. */
internal const val WS_PROTOCOL: String = "v2.bsatn.spacetimedb"

/**
 * Low-level WebSocket transport for SpacetimeDB.
 *
 * Handles connection establishment, message encoding/decoding, and server message decompression.
 * Does not implement higher-level connection management (reconnection, callbacks, subscriptions).
 *
 * @param httpClient A Ktor [HttpClient] with the [WebSockets] plugin installed. If not provided, a
 *   default client is created.
 */
internal class WebSocketTransport(private val httpClient: HttpClient = defaultHttpClient()) {
    /**
     * Establishes a WebSocket connection to a SpacetimeDB instance.
     *
     * @param host Base URI of the SpacetimeDB instance (e.g. "http://localhost:3000")
     * @param databaseName Name of the database to connect to
     * @param compression Compression preference for server messages
     * @param token Optional JWT for authentication
     * @param connectionId Optional connection ID for reconnection
     * @param confirmed Optional flag to enable/disable confirmed reads
     * @return A [WebSocketConnection] for sending and receiving messages
     */
    suspend fun connect(
        host: String,
        databaseName: String,
        compression: Compression,
        token: String? = null,
        connectionId: ConnectionId? = null,
        confirmed: Boolean? = null,
    ): WebSocketConnection {
        val uri =
            buildSpacetimeUri(
                host = host,
                databaseName = databaseName,
                compression = compression,
                connectionId = connectionId,
                confirmed = confirmed,
            )

        val session =
            httpClient.webSocketSession(urlString = uri) {
                headers.append(HttpHeaders.SecWebSocketProtocol, WS_PROTOCOL)
                if (token != null) {
                    headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
            }

        return KtorWebSocketConnection(session)
    }
}

/**
 * An active WebSocket connection to a SpacetimeDB instance.
 *
 * Provides typed send/receive for [ClientMessage] and [ServerMessage], with automatic BSATN
 * encoding and server message decompression.
 */
internal abstract class WebSocketConnection {
    /** Flow of decoded [ServerMessage]s from the server. */
    abstract val serverMessages: Flow<ServerMessage>

    /** Sends a [ClientMessage] to the server. */
    abstract suspend fun send(message: ClientMessage)

    /** Closes the connection. */
    abstract suspend fun close()
}

/** Ktor-based [WebSocketConnection] implementation. */
internal class KtorWebSocketConnection(private val session: DefaultClientWebSocketSession) :
    WebSocketConnection() {
    override val serverMessages: Flow<ServerMessage> =
        session.incoming.receiveAsFlow().mapNotNull { frame ->
            when (frame) {
                is Frame.Binary -> {
                    val decompressed = decompressServerMessage(frame.readBytes())
                    Bsatn.decodeFromByteArray(ServerMessageSerializer, decompressed)
                }
                else -> null
            }
        }

    override suspend fun send(message: ClientMessage) {
        val bytes = Bsatn.encodeToByteArray(ClientMessageSerializer, message)
        session.send(Frame.Binary(fin = true, data = bytes))
    }

    override suspend fun close() {
        session.close()
    }
}

private fun defaultHttpClient(): HttpClient = HttpClient { install(WebSockets) }
