package dev.sanson.spacetimedb

import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A point in time, backed by [Instant].
 *
 * Serialized as microseconds since the Unix epoch (signed 64-bit LE integer) in BSATN.
 */
@Serializable(with = TimestampSerializer::class)
@JvmInline
public value class Timestamp(public val instant: Instant) : Comparable<Timestamp> {
    public companion object {
        public val UNIX_EPOCH: Timestamp = Timestamp(Instant.fromEpochSeconds(0))

        /** Creates a [Timestamp] from microseconds since the Unix epoch. */
        public fun fromEpochMicroseconds(micros: Long): Timestamp {
            val seconds = micros.floorDiv(MICROS_PER_SECOND)
            val nanoAdjustment = micros.mod(MICROS_PER_SECOND) * NANOS_PER_MICRO
            return Timestamp(Instant.fromEpochSeconds(seconds, nanoAdjustment))
        }

        private const val MICROS_PER_SECOND = 1_000_000L
        private const val NANOS_PER_MICRO = 1_000L
    }

    /** Returns the number of microseconds since the Unix epoch. */
    public val epochMicroseconds: Long
        get() = instant.epochSeconds * 1_000_000L + instant.nanosecondsOfSecond / 1_000

    override fun compareTo(other: Timestamp): Int = instant.compareTo(other.instant)

    override fun toString(): String = "Timestamp($instant)"
}

internal object TimestampSerializer : KSerializer<Timestamp> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.sanson.spacetimedb.Timestamp", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Timestamp) {
        encoder.encodeLong(value.epochMicroseconds)
    }

    override fun deserialize(decoder: Decoder): Timestamp {
        return Timestamp.fromEpochMicroseconds(decoder.decodeLong())
    }
}
