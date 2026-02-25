package dev.sanson.spacetimedb

import dev.drewhamilton.poko.Poko
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A point in time, represented as microseconds since the Unix epoch.
 *
 * Serialized as a signed 64-bit little-endian integer in BSATN.
 */
@Poko
@Serializable(with = TimestampSerializer::class)
public class Timestamp(public val microsSinceUnixEpoch: Long) : Comparable<Timestamp> {
    public companion object {
        public val UNIX_EPOCH: Timestamp = Timestamp(0L)
    }

    override fun compareTo(other: Timestamp): Int =
        microsSinceUnixEpoch.compareTo(other.microsSinceUnixEpoch)

    override fun toString(): String = "Timestamp(${microsSinceUnixEpoch}µs)"
}

internal object TimestampSerializer : KSerializer<Timestamp> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.sanson.spacetimedb.Timestamp", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Timestamp) {
        encoder.encodeLong(value.microsSinceUnixEpoch)
    }

    override fun deserialize(decoder: Decoder): Timestamp {
        return Timestamp(decoder.decodeLong())
    }
}
