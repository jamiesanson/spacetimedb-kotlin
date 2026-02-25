package dev.sanson.spacetimedb.bsatn

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.SerializersModule

/**
 * Encodes values to the BSATN binary format.
 *
 * BSATN encoding rules:
 * - Primitives: little-endian fixed-size
 * - Strings/ByteArrays: u32LE length prefix + bytes
 * - Lists/Arrays: u32LE count + elements
 * - Products (data classes): fields concatenated in order, no prefix
 * - Sums (sealed classes): u8 tag + variant payload
 * - Nullable: sum type — tag 0 = Some(value), tag 1 = None
 */
@OptIn(ExperimentalSerializationApi::class)
internal class BsatnEncoder(
    private val buffer: BsatnBuffer,
    override val serializersModule: SerializersModule,
) : AbstractEncoder() {

    override fun encodeBoolean(value: Boolean) {
        buffer.writeByte(if (value) 1 else 0)
    }

    override fun encodeByte(value: Byte) {
        buffer.writeByte(value.toInt())
    }

    override fun encodeShort(value: Short) {
        buffer.writeShortLE(value)
    }

    override fun encodeInt(value: Int) {
        buffer.writeIntLE(value)
    }

    override fun encodeLong(value: Long) {
        buffer.writeLongLE(value)
    }

    override fun encodeFloat(value: Float) {
        buffer.writeIntLE(value.toBits())
    }

    override fun encodeDouble(value: Double) {
        buffer.writeLongLE(value.toBits())
    }

    override fun encodeChar(value: Char) {
        encodeString(value.toString())
    }

    override fun encodeString(value: String) {
        val bytes = value.encodeToByteArray()
        buffer.writeIntLE(bytes.size)
        buffer.writeBytes(bytes)
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        buffer.writeByte(index)
    }

    override fun encodeNull() {
        // BSATN nullable: tag 1 = None
        buffer.writeByte(1)
    }

    override fun encodeNotNullMark() {
        // BSATN nullable: tag 0 = Some
        buffer.writeByte(0)
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        if (descriptor.kind == StructureKind.MAP) {
            throw BsatnEncodeException("BSATN does not support map types")
        }
        // Arrays/Lists: write u32LE count first
        buffer.writeIntLE(collectionSize)
        return this
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return when (descriptor.kind) {
            PolymorphicKind.SEALED -> SealedClassEncoder(buffer, serializersModule, descriptor)
            else -> this
        }
    }

    // Extension methods for big integer types
    fun encodeU128(value: U128) {
        buffer.writeLongLE(value.lo.toLong())
        buffer.writeLongLE(value.hi.toLong())
    }

    fun encodeI128(value: I128) {
        buffer.writeLongLE(value.lo.toLong())
        buffer.writeLongLE(value.hi.toLong())
    }

    fun encodeU256(value: U256) {
        buffer.writeLongLE(value.w0.toLong())
        buffer.writeLongLE(value.w1.toLong())
        buffer.writeLongLE(value.w2.toLong())
        buffer.writeLongLE(value.w3.toLong())
    }

    fun encodeI256(value: I256) {
        buffer.writeLongLE(value.w0.toLong())
        buffer.writeLongLE(value.w1.toLong())
        buffer.writeLongLE(value.w2.toLong())
        buffer.writeLongLE(value.w3.toLong())
    }
}

/**
 * Specialized encoder for sealed class hierarchies.
 *
 * kotlinx.serialization encodes sealed classes as a 2-element structure:
 * [type_discriminator_string, content]. We intercept this to write BSATN sum types:
 * u8 tag (ordinal index of the subclass) + encoded content.
 */
@OptIn(ExperimentalSerializationApi::class)
private class SealedClassEncoder(
    private val buffer: BsatnBuffer,
    override val serializersModule: SerializersModule,
    private val sealedDescriptor: SerialDescriptor,
) : AbstractEncoder() {

    override fun encodeString(value: String) {
        // This is the type discriminator string. Find its index in the sealed class hierarchy.
        // element(1) of the sealed descriptor is the polymorphic content,
        // which has sub-descriptors for each variant. Match by serial name.
        val polyDesc = sealedDescriptor.getElementDescriptor(1)
        var tag = -1
        for (i in 0 until polyDesc.elementsCount) {
            if (polyDesc.getElementDescriptor(i).serialName == value) {
                tag = i
                break
            }
        }
        if (tag < 0) error("Unknown sealed subclass: $value")
        buffer.writeByte(tag)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return when (descriptor.kind) {
            PolymorphicKind.SEALED -> this
            else -> BsatnEncoder(buffer, serializersModule)
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        // no-op
    }

    override fun encodeBoolean(value: Boolean) = BsatnEncoder(buffer, serializersModule).encodeBoolean(value)
    override fun encodeByte(value: Byte) = BsatnEncoder(buffer, serializersModule).encodeByte(value)
    override fun encodeShort(value: Short) = BsatnEncoder(buffer, serializersModule).encodeShort(value)
    override fun encodeInt(value: Int) = BsatnEncoder(buffer, serializersModule).encodeInt(value)
    override fun encodeLong(value: Long) = BsatnEncoder(buffer, serializersModule).encodeLong(value)
    override fun encodeFloat(value: Float) = BsatnEncoder(buffer, serializersModule).encodeFloat(value)
    override fun encodeDouble(value: Double) = BsatnEncoder(buffer, serializersModule).encodeDouble(value)

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        buffer.writeByte(index)
    }
}
