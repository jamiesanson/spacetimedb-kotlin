package dev.sanson.spacetimedb

import dev.drewhamilton.poko.Poko
import dev.sanson.spacetimedb.bsatn.U128
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A unique identifier for a client connection to a SpacetimeDB database.
 *
 * Wraps a 128-bit unsigned integer, serialized as 16 little-endian bytes in BSATN.
 */
@Poko
@Serializable(with = ConnectionIdSerializer::class)
public class ConnectionId(public val value: U128) : Comparable<ConnectionId> {
    public companion object {
        public val ZERO: ConnectionId = ConnectionId(U128.ZERO)
    }

    override fun compareTo(other: ConnectionId): Int = value.compareTo(other.value)

    override fun toString(): String = "ConnectionId($value)"
}

internal object ConnectionIdSerializer : KSerializer<ConnectionId> {
    private val delegate = U128.serializer()
    override val descriptor: SerialDescriptor = SerialDescriptor("dev.sanson.spacetimedb.ConnectionId", delegate.descriptor)

    override fun serialize(encoder: Encoder, value: ConnectionId) {
        encoder.encodeSerializableValue(delegate, value.value)
    }

    override fun deserialize(decoder: Decoder): ConnectionId {
        return ConnectionId(decoder.decodeSerializableValue(delegate))
    }
}
