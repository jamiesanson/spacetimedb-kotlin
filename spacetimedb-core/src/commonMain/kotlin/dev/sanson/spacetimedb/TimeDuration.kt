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
 * A signed duration of time in microseconds.
 *
 * Unlike `kotlin.time.Duration`, this supports negative values (for representing
 * durations in the past). Serialized as a signed 64-bit little-endian integer in BSATN.
 */
@Poko
@Serializable(with = TimeDurationSerializer::class)
public class TimeDuration(public val micros: Long) : Comparable<TimeDuration> {
    public companion object {
        public val ZERO: TimeDuration = TimeDuration(0L)
    }

    override fun compareTo(other: TimeDuration): Int =
        micros.compareTo(other.micros)

    override fun toString(): String = "TimeDuration(${micros}µs)"
}

internal object TimeDurationSerializer : KSerializer<TimeDuration> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.sanson.spacetimedb.TimeDuration", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: TimeDuration) {
        encoder.encodeLong(value.micros)
    }

    override fun deserialize(decoder: Decoder): TimeDuration {
        return TimeDuration(decoder.decodeLong())
    }
}
