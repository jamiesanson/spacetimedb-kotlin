package dev.sanson.spacetimedb.bsatn

import dev.drewhamilton.poko.Poko
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 128-bit unsigned integer, stored as two [ULong] halves in little-endian order.
 * [lo] is the least-significant 64 bits, [hi] is the most-significant 64 bits.
 */
@Poko
@Serializable(with = U128Serializer::class)
public class U128(public val lo: ULong, public val hi: ULong) : Comparable<U128> {
    public companion object {
        public val ZERO: U128 = U128(0u, 0u)
        public val MAX: U128 = U128(ULong.MAX_VALUE, ULong.MAX_VALUE)
    }

    override fun compareTo(other: U128): Int {
        val hiCmp = hi.compareTo(other.hi)
        return if (hiCmp != 0) hiCmp else lo.compareTo(other.lo)
    }

    override fun toString(): String {
        if (hi == 0uL) return lo.toString()
        return "U128(lo=$lo, hi=$hi)"
    }
}

/**
 * 128-bit signed integer, stored as two [ULong] halves in little-endian order.
 * Interpreted as two's complement.
 */
@Poko
@Serializable(with = I128Serializer::class)
public class I128(public val lo: ULong, public val hi: ULong) : Comparable<I128> {
    public companion object {
        public val ZERO: I128 = I128(0u, 0u)
        public val MIN: I128 = I128(0u, 0x8000000000000000uL)
        public val MAX: I128 = I128(ULong.MAX_VALUE, 0x7FFFFFFFFFFFFFFFuL)
    }

    override fun compareTo(other: I128): Int {
        val hiCmp = hi.toLong().compareTo(other.hi.toLong())
        return if (hiCmp != 0) hiCmp else lo.compareTo(other.lo)
    }
}

/**
 * 256-bit unsigned integer, stored as four [ULong] words in little-endian order.
 */
@Poko
@Serializable(with = U256Serializer::class)
public class U256(public val w0: ULong, public val w1: ULong, public val w2: ULong, public val w3: ULong) : Comparable<U256> {
    public companion object {
        public val ZERO: U256 = U256(0u, 0u, 0u, 0u)
        public val MAX: U256 = U256(ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE)
    }

    override fun compareTo(other: U256): Int {
        var cmp = w3.compareTo(other.w3)
        if (cmp != 0) return cmp
        cmp = w2.compareTo(other.w2)
        if (cmp != 0) return cmp
        cmp = w1.compareTo(other.w1)
        if (cmp != 0) return cmp
        return w0.compareTo(other.w0)
    }
}

/**
 * 256-bit signed integer, stored as four [ULong] words in little-endian order.
 * Interpreted as two's complement.
 */
@Poko
@Serializable(with = I256Serializer::class)
public class I256(public val w0: ULong, public val w1: ULong, public val w2: ULong, public val w3: ULong) : Comparable<I256> {
    public companion object {
        public val ZERO: I256 = I256(0u, 0u, 0u, 0u)
    }

    override fun compareTo(other: I256): Int {
        val cmp = w3.toLong().compareTo(other.w3.toLong())
        if (cmp != 0) return cmp
        val cmp2 = w2.compareTo(other.w2)
        if (cmp2 != 0) return cmp2
        val cmp1 = w1.compareTo(other.w1)
        if (cmp1 != 0) return cmp1
        return w0.compareTo(other.w0)
    }
}

// -- Serializers --

internal object U128Serializer : KSerializer<U128> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.sanson.spacetimedb.bsatn.U128", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: U128) {
        if (encoder is BsatnEncoder) {
            encoder.encodeU128(value)
        } else {
            encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): U128 {
        if (decoder is BsatnDecoder) {
            return decoder.decodeU128()
        }
        throw UnsupportedOperationException("U128 can only be deserialized from BSATN")
    }
}

internal object I128Serializer : KSerializer<I128> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.sanson.spacetimedb.bsatn.I128", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: I128) {
        if (encoder is BsatnEncoder) {
            encoder.encodeI128(value)
        } else {
            encoder.encodeString("${value.hi}:${value.lo}")
        }
    }

    override fun deserialize(decoder: Decoder): I128 {
        if (decoder is BsatnDecoder) {
            return decoder.decodeI128()
        }
        throw UnsupportedOperationException("I128 can only be deserialized from BSATN")
    }
}

internal object U256Serializer : KSerializer<U256> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.sanson.spacetimedb.bsatn.U256", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: U256) {
        if (encoder is BsatnEncoder) {
            encoder.encodeU256(value)
        } else {
            encoder.encodeString("${value.w3}:${value.w2}:${value.w1}:${value.w0}")
        }
    }

    override fun deserialize(decoder: Decoder): U256 {
        if (decoder is BsatnDecoder) {
            return decoder.decodeU256()
        }
        throw UnsupportedOperationException("U256 can only be deserialized from BSATN")
    }
}

internal object I256Serializer : KSerializer<I256> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.sanson.spacetimedb.bsatn.I256", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: I256) {
        if (encoder is BsatnEncoder) {
            encoder.encodeI256(value)
        } else {
            encoder.encodeString("${value.w3}:${value.w2}:${value.w1}:${value.w0}")
        }
    }

    override fun deserialize(decoder: Decoder): I256 {
        if (decoder is BsatnDecoder) {
            return decoder.decodeI256()
        }
        throw UnsupportedOperationException("I256 can only be deserialized from BSATN")
    }
}
