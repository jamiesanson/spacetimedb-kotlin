package dev.sanson.spacetimedb.protocol

import kotlin.jvm.JvmInline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Opaque identifier for a subscription query set. */
@Serializable @JvmInline internal value class QuerySetId(val id: UInt)

/** Flags for [ClientMessage.CallReducer] requests. */
@Serializable
@JvmInline
internal value class CallReducerFlags(val bits: UByte) {
    internal companion object {
        val Default = CallReducerFlags(0u)
    }
}

/** Flags for [ClientMessage.Unsubscribe] requests. */
@Serializable
@JvmInline
internal value class UnsubscribeFlags(val bits: UByte) {
    internal companion object {
        val Default = UnsubscribeFlags(0u)
        val SendDroppedRows = UnsubscribeFlags(1u)
    }
}

/** Flags for [ClientMessage.CallProcedure] requests. */
@Serializable
@JvmInline
internal value class CallProcedureFlags(val bits: UByte) {
    internal companion object {
        val Default = CallProcedureFlags(0u)
    }
}

/**
 * Messages sent from the client to the SpacetimeDB server over WebSocket v2.
 *
 * Uses [ClientMessageSerializer] for explicit tag ordering matching the Rust enum.
 */
@Serializable(with = ClientMessageSerializer::class)
internal sealed class ClientMessage {
    @Serializable
    data class Subscribe(
        val requestId: UInt,
        val querySetId: QuerySetId,
        val queryStrings: List<String>,
    ) : ClientMessage()

    @Serializable
    data class Unsubscribe(
        val requestId: UInt,
        val querySetId: QuerySetId,
        val flags: UnsubscribeFlags,
    ) : ClientMessage()

    @Serializable
    data class OneOffQuery(val requestId: UInt, val queryString: String) : ClientMessage()

    @Serializable
    data class CallReducer(
        val requestId: UInt,
        val flags: CallReducerFlags,
        val reducer: String,
        val args: ByteArray,
    ) : ClientMessage()

    @Serializable
    data class CallProcedure(
        val requestId: UInt,
        val flags: CallProcedureFlags,
        val procedure: String,
        val args: ByteArray,
    ) : ClientMessage()
}

internal object ClientMessageSerializer :
    KSerializer<ClientMessage> by TaggedSumSerializer(
        "ClientMessage",
        arrayOf(
            variant(ClientMessage.Subscribe.serializer()),
            variant(ClientMessage.Unsubscribe.serializer()),
            variant(ClientMessage.OneOffQuery.serializer()),
            variant(ClientMessage.CallReducer.serializer()),
            variant(ClientMessage.CallProcedure.serializer()),
        ),
    )
