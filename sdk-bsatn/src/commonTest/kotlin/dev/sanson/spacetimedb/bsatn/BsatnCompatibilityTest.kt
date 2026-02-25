package dev.sanson.spacetimedb.bsatn

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Tests encoding/decoding against known BSATN byte sequences
 * that match the Rust SpacetimeDB BSATN encoder output.
 */
class BsatnCompatibilityTest {

    // -- Primitive compatibility --

    @Test
    fun `bool true matches Rust`() {
        // Rust: bsatn::to_vec(&true) == [1]
        assertContentEquals(byteArrayOf(1), Bsatn.encodeToByteArray(Boolean.serializer(), true))
    }

    @Test
    fun `bool false matches Rust`() {
        // Rust: bsatn::to_vec(&false) == [0]
        assertContentEquals(byteArrayOf(0), Bsatn.encodeToByteArray(Boolean.serializer(), false))
    }

    @Test
    fun `u8 42 matches Rust`() {
        // Rust: bsatn::to_vec(&42u8) == [42]
        assertContentEquals(byteArrayOf(42), Bsatn.encodeToByteArray(Byte.serializer(), 42))
    }

    @Test
    fun `i32 negative one matches Rust`() {
        // Rust: bsatn::to_vec(&(-1i32)) == [0xFF, 0xFF, 0xFF, 0xFF]
        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            Bsatn.encodeToByteArray(Int.serializer(), -1),
        )
    }

    @Test
    fun `f32 one-point-five matches Rust`() {
        // Rust: bsatn::to_vec(&1.5f32) == f32::to_bits(1.5) as LE bytes
        // 1.5f32.to_bits() == 0x3FC00000
        // LE: [0x00, 0x00, 0xC0, 0x3F]
        assertContentEquals(
            byteArrayOf(0x00, 0x00, 0xC0.toByte(), 0x3F),
            Bsatn.encodeToByteArray(Float.serializer(), 1.5f),
        )
    }

    @Test
    fun `string hello matches Rust`() {
        // Rust: bsatn::to_vec(&"hello".to_string())
        // == [5, 0, 0, 0, 104, 101, 108, 108, 111]
        assertContentEquals(
            byteArrayOf(5, 0, 0, 0, 104, 101, 108, 108, 111),
            Bsatn.encodeToByteArray(String.serializer(), "hello"),
        )
    }

    // -- Product compatibility --

    @Serializable
    data class TestPoint(val x: Int, val y: Int)

    @Test
    fun `product struct matches Rust field order`() {
        // Rust struct { x: i32, y: i32 } with x=1, y=2
        // encodes as: [1,0,0,0, 2,0,0,0]
        assertContentEquals(
            byteArrayOf(1, 0, 0, 0, 2, 0, 0, 0),
            Bsatn.encodeToByteArray(TestPoint.serializer(), TestPoint(1, 2)),
        )
    }

    // -- Option compatibility --

    @Test
    fun `option some matches Rust`() {
        // Rust: bsatn::to_vec(&Some(42i32)) == [0, 42, 0, 0, 0]
        // Tag 0 = Some, then value
        val bytes = Bsatn.encodeToByteArray(
            Int.serializer().nullable,
            42,
        )
        assertContentEquals(byteArrayOf(0, 42, 0, 0, 0), bytes)
    }

    @Test
    fun `option none matches Rust`() {
        // Rust: bsatn::to_vec(&None::<i32>) == [1]
        // Tag 1 = None
        val bytes = Bsatn.encodeToByteArray(
            Int.serializer().nullable,
            null,
        )
        assertContentEquals(byteArrayOf(1), bytes)
    }

    // -- Enum compatibility --

    @Serializable
    enum class SimpleEnum { Zero, One, Two }

    @Test
    fun `enum tag matches Rust ordinal`() {
        // Rust enum variants are tagged 0, 1, 2...
        assertContentEquals(byteArrayOf(0), Bsatn.encodeToByteArray(SimpleEnum.serializer(), SimpleEnum.Zero))
        assertContentEquals(byteArrayOf(1), Bsatn.encodeToByteArray(SimpleEnum.serializer(), SimpleEnum.One))
        assertContentEquals(byteArrayOf(2), Bsatn.encodeToByteArray(SimpleEnum.serializer(), SimpleEnum.Two))
    }

    // -- Vec compatibility --

    @Test
    fun `vec of i32 matches Rust`() {
        // Rust: bsatn::to_vec(&vec![1i32, 2, 3])
        // == [3,0,0,0, 1,0,0,0, 2,0,0,0, 3,0,0,0]
        val bytes = Bsatn.encodeToByteArray(
            kotlinx.serialization.builtins.ListSerializer(Int.serializer()),
            listOf(1, 2, 3),
        )
        assertContentEquals(
            byteArrayOf(3, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0),
            bytes,
        )
    }

    // -- Cross-decode: decode Rust-produced bytes --

    @Test
    fun `decode Rust-produced product bytes`() {
        // These bytes represent TestPoint { x: 100, y: 200 } from Rust
        val rustBytes = byteArrayOf(100, 0, 0, 0, -56, 0, 0, 0) // 200 = 0xC8
        val decoded = Bsatn.decodeFromByteArray(TestPoint.serializer(), rustBytes)
        assertEquals(TestPoint(100, 200), decoded)
    }

    @Test
    fun `decode Rust-produced string bytes`() {
        // "test" in BSATN: [4, 0, 0, 0, 116, 101, 115, 116]
        val rustBytes = byteArrayOf(4, 0, 0, 0, 116, 101, 115, 116)
        assertEquals("test", Bsatn.decodeFromByteArray(String.serializer(), rustBytes))
    }
}
