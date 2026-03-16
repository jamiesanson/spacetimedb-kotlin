package dev.sanson.spacetimedb.bsatn

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * The BSATN (Binary SpacetimeDB Algebraic Type Notation) serialization format.
 *
 * BSATN is a compact binary format used by SpacetimeDB for wire communication.
 *
 * ## Format rules
 * - **Primitives**: Little-endian, fixed-size encoding
 * - **Strings**: u32LE length prefix + UTF-8 bytes
 * - **ByteArrays**: u32LE length prefix + raw bytes
 * - **Lists**: u32LE element count + encoded elements
 * - **Products** (data classes): Fields concatenated in declaration order
 * - **Sums** (sealed classes/enums): u8 variant tag + payload
 * - **Nullable types**: Sum — tag 0 = Some(value), tag 1 = None
 *
 * ```kotlin
 * @Serializable
 * data class Player(val name: String, val score: Int)
 *
 * val bytes = Bsatn.encodeToByteArray(Player("Alice", 42))
 * val player = Bsatn.decodeFromByteArray<Player>(bytes)
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
public sealed class Bsatn(override val serializersModule: SerializersModule) : BinaryFormat {

    public companion object Default : Bsatn(EmptySerializersModule())

    override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val buffer = BsatnBuffer()
        val encoder = BsatnEncoder(buffer, serializersModule)
        encoder.encodeSerializableValue(serializer, value)
        return buffer.toByteArray()
    }

    override fun <T> decodeFromByteArray(
        deserializer: DeserializationStrategy<T>,
        bytes: ByteArray,
    ): T {
        val reader = BsatnReader(bytes)
        val decoder = BsatnDecoder(reader, serializersModule)
        return decoder.decodeSerializableValue(deserializer)
    }
}
