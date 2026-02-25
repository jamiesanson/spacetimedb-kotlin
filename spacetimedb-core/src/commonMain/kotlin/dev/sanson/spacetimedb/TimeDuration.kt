package dev.sanson.spacetimedb

import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A signed duration of time, backed by [Duration].
 *
 * Serialized as microseconds (signed 64-bit LE integer) in BSATN.
 */
@Serializable(with = TimeDurationSerializer::class)
@JvmInline
public value class TimeDuration(public val duration: Duration) : Comparable<TimeDuration> {
    public companion object {
        public val ZERO: TimeDuration = TimeDuration(Duration.ZERO)
    }

    override fun compareTo(other: TimeDuration): Int =
        duration.compareTo(other.duration)

    override fun toString(): String = "TimeDuration($duration)"
}

internal object TimeDurationSerializer : KSerializer<TimeDuration> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.sanson.spacetimedb.TimeDuration", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: TimeDuration) {
        encoder.encodeLong(value.duration.inWholeMicroseconds)
    }

    override fun deserialize(decoder: Decoder): TimeDuration {
        return TimeDuration(decoder.decodeLong().microseconds)
    }
}
