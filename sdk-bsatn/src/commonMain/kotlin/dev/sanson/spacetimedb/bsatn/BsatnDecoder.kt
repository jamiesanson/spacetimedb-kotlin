package dev.sanson.spacetimedb.bsatn

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule

/**
 * Decodes values from the BSATN binary format.
 *
 * Mirror of [BsatnEncoder] — see that class for format documentation.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class BsatnDecoder(
    private val reader: BsatnReader,
    override val serializersModule: SerializersModule,
) : AbstractDecoder() {

    internal var elementIndex = 0
    internal var elementsCount = 0

    override fun decodeBoolean(): Boolean {
        return when (val byte = reader.readByte()) {
            0 -> false
            1 -> true
            else -> throw BsatnDecodeException("Invalid bool byte: $byte (expected 0 or 1)")
        }
    }

    override fun decodeByte(): Byte {
        return reader.readByte().toByte()
    }

    override fun decodeShort(): Short {
        return reader.readShortLE()
    }

    override fun decodeInt(): Int {
        return reader.readIntLE()
    }

    override fun decodeLong(): Long {
        return reader.readLongLE()
    }

    override fun decodeFloat(): Float {
        return Float.fromBits(reader.readIntLE())
    }

    override fun decodeDouble(): Double {
        return Double.fromBits(reader.readLongLE())
    }

    override fun decodeChar(): Char {
        return decodeString().first()
    }

    override fun decodeString(): String {
        val len = reader.readIntLE()
        val bytes = reader.readBytes(len)
        return bytes.decodeToString()
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        return reader.readByte()
    }

    override fun decodeNotNullMark(): Boolean {
        val tag = reader.readByte()
        // tag 0 = Some (not null), tag 1 = None (null)
        return tag == 0
    }

    override fun decodeNull(): Nothing? {
        return null
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        val size = reader.readIntLE()
        elementsCount = size
        return size
    }

    // BSATN always encodes size upfront, so sequential decoding is correct
    // for both collections (u32LE count prefix) and products (known field count).
    override fun decodeSequentially(): Boolean = true

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return when (descriptor.kind) {
            PolymorphicKind.SEALED -> SealedClassDecoder(reader, serializersModule, descriptor)
            StructureKind.LIST, StructureKind.MAP -> {
                BsatnDecoder(reader, serializersModule)
            }
            else -> {
                BsatnDecoder(reader, serializersModule).also {
                    it.elementsCount = descriptor.elementsCount
                }
            }
        }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= elementsCount && elementsCount > 0) {
            return CompositeDecoder.DECODE_DONE
        }

        return when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> {
                if (elementIndex >= descriptor.elementsCount) {
                    CompositeDecoder.DECODE_DONE
                } else {
                    elementIndex++
                    elementIndex - 1
                }
            }
            StructureKind.LIST -> {
                if (elementIndex >= elementsCount) {
                    CompositeDecoder.DECODE_DONE
                } else {
                    elementIndex++
                    elementIndex - 1
                }
            }
            StructureKind.MAP -> {
                if (elementIndex >= elementsCount * 2) {
                    CompositeDecoder.DECODE_DONE
                } else {
                    elementIndex++
                    elementIndex - 1
                }
            }
            else -> {
                if (elementIndex >= descriptor.elementsCount) {
                    CompositeDecoder.DECODE_DONE
                } else {
                    elementIndex++
                    elementIndex - 1
                }
            }
        }
    }

    // Extension methods for big integer types
    fun decodeU128(): U128 {
        val lo = reader.readLongLE().toULong()
        val hi = reader.readLongLE().toULong()
        return U128(lo, hi)
    }

    fun decodeI128(): I128 {
        val lo = reader.readLongLE().toULong()
        val hi = reader.readLongLE().toULong()
        return I128(lo, hi)
    }

    fun decodeU256(): U256 {
        val w0 = reader.readLongLE().toULong()
        val w1 = reader.readLongLE().toULong()
        val w2 = reader.readLongLE().toULong()
        val w3 = reader.readLongLE().toULong()
        return U256(w0, w1, w2, w3)
    }

    fun decodeI256(): I256 {
        val w0 = reader.readLongLE().toULong()
        val w1 = reader.readLongLE().toULong()
        val w2 = reader.readLongLE().toULong()
        val w3 = reader.readLongLE().toULong()
        return I256(w0, w1, w2, w3)
    }
}

/**
 * Specialized decoder for sealed class hierarchies.
 *
 * Reads a u8 tag to determine the subclass variant, then resolves the
 * kotlinx.serialization serial name and delegates content decoding.
 */
@OptIn(ExperimentalSerializationApi::class)
private class SealedClassDecoder(
    private val reader: BsatnReader,
    override val serializersModule: SerializersModule,
    sealedDescriptor: SerialDescriptor,
) : AbstractDecoder() {

    private var elementIndex = 0
    private val resolvedSerialName: String

    init {
        // Read the u8 tag and resolve to serial name immediately
        val tag = reader.readByte()
        val polyDesc = sealedDescriptor.getElementDescriptor(1)
        if (tag < 0 || tag >= polyDesc.elementsCount) {
            throw BsatnDecodeException("Invalid sealed class tag: $tag (max ${polyDesc.elementsCount - 1})")
        }
        resolvedSerialName = polyDesc.getElementDescriptor(tag).serialName
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return when (descriptor.kind) {
            PolymorphicKind.SEALED -> this
            else -> {
                // Content structure — delegate to standard decoder
                BsatnDecoder(reader, serializersModule).also {
                    it.elementsCount = descriptor.elementsCount
                }
            }
        }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        // Sealed class structure: [0] = type (string), [1] = content
        if (elementIndex >= 2) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun decodeString(): String {
        // Return the pre-resolved serial name as the type discriminator
        return resolvedSerialName
    }
}
