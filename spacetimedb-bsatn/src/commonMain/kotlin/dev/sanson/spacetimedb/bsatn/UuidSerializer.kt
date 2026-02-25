package dev.sanson.spacetimedb.bsatn

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes [Uuid] as a 128-bit value in BSATN (16 little-endian bytes, same layout as [U128]).
 *
 * Use with `@Serializable(with = UuidSerializer::class)` on [Uuid] properties.
 */
@OptIn(ExperimentalUuidApi::class)
public object UuidSerializer : KSerializer<Uuid> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.sanson.spacetimedb.bsatn.Uuid", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uuid) {
        if (encoder is BsatnEncoder) {
            value.toLongs { msb, lsb ->
                encoder.encodeU128(U128(lo = lsb.toULong(), hi = msb.toULong()))
            }
        } else {
            encoder.encodeString(value.toHexString())
        }
    }

    override fun deserialize(decoder: Decoder): Uuid {
        if (decoder is BsatnDecoder) {
            val u128 = decoder.decodeU128()
            return Uuid.fromLongs(
                mostSignificantBits = u128.hi.toLong(),
                leastSignificantBits = u128.lo.toLong(),
            )
        }
        return Uuid.parse(decoder.decodeString())
    }
}
