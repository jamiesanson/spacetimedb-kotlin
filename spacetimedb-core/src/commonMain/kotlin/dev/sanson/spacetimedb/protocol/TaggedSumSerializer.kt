package dev.sanson.spacetimedb.protocol

import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A custom serializer for BSATN sum types that uses explicit tag ordering.
 *
 * kotlinx.serialization orders sealed class variants alphabetically by serial name, but BSATN wire
 * compatibility requires Rust enum declaration order. This serializer writes a u8 tag byte followed
 * by the variant's product data.
 */
internal class TaggedSumSerializer<T : Any>(
    serialName: String,
    private val variants: Array<out TaggedVariant<out T>>,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(serialName)

    override fun serialize(encoder: Encoder, value: T) {
        val index = variants.indexOfFirst { it.kClass.isInstance(value) }
        require(index >= 0) { "Unknown variant of ${descriptor.serialName}: ${value::class}" }
        encoder.encodeByte(index.toByte())
        @Suppress("UNCHECKED_CAST")
        (variants[index].serializer as KSerializer<T>).serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): T {
        val tag = decoder.decodeByte().toInt()
        require(tag in variants.indices) { "Invalid tag $tag for ${descriptor.serialName}" }
        return variants[tag].serializer.deserialize(decoder)
    }
}

internal class TaggedVariant<V : Any>(val kClass: KClass<V>, val serializer: KSerializer<V>)

internal inline fun <reified V : Any> variant(serializer: KSerializer<V>): TaggedVariant<V> =
    TaggedVariant(V::class, serializer)
