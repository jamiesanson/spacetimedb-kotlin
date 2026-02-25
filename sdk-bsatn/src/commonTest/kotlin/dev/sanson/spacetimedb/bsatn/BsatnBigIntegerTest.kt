package dev.sanson.spacetimedb.bsatn

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BsatnBigIntegerTest {

    // -- U128 --

    @Test
    fun `encode U128 zero`() {
        val bytes = Bsatn.encodeToByteArray(U128.serializer(), U128.ZERO)
        assertEquals(16, bytes.size)
        assertContentEquals(ByteArray(16), bytes)
    }

    @Test
    fun `encode U128 small value`() {
        // lo=42, hi=0
        val bytes = Bsatn.encodeToByteArray(U128.serializer(), U128(42u, 0u))
        assertEquals(16, bytes.size)
        // lo=42 as i64LE + hi=0 as i64LE
        assertEquals(42, bytes[0].toInt())
        assertEquals(0, bytes[8].toInt())
    }

    @Test
    fun `roundtrip U128`() {
        val values = listOf(
            U128.ZERO,
            U128(1u, 0u),
            U128(ULong.MAX_VALUE, 0u),
            U128(0u, 1u),
            U128(ULong.MAX_VALUE, ULong.MAX_VALUE),
            U128(0x0102030405060708u, 0x090A0B0C0D0E0F10u),
        )
        for (v in values) {
            val bytes = Bsatn.encodeToByteArray(U128.serializer(), v)
            assertEquals(v, Bsatn.decodeFromByteArray(U128.serializer(), bytes))
        }
    }

    // -- I128 --

    @Test
    fun `roundtrip I128`() {
        val values = listOf(
            I128.ZERO,
            I128(1u, 0u),
            I128.MIN,
            I128.MAX,
            I128(ULong.MAX_VALUE, ULong.MAX_VALUE), // -1
        )
        for (v in values) {
            val bytes = Bsatn.encodeToByteArray(I128.serializer(), v)
            assertEquals(v, Bsatn.decodeFromByteArray(I128.serializer(), bytes))
        }
    }

    // -- U256 --

    @Test
    fun `encode U256 zero`() {
        val bytes = Bsatn.encodeToByteArray(U256.serializer(), U256.ZERO)
        assertEquals(32, bytes.size)
        assertContentEquals(ByteArray(32), bytes)
    }

    @Test
    fun `roundtrip U256`() {
        val values = listOf(
            U256.ZERO,
            U256(1u, 0u, 0u, 0u),
            U256.MAX,
            U256(0x0102030405060708u, 0x090A0B0C0D0E0F10u, 0x1112131415161718u, 0x191A1B1C1D1E1F20u),
        )
        for (v in values) {
            val bytes = Bsatn.encodeToByteArray(U256.serializer(), v)
            assertEquals(v, Bsatn.decodeFromByteArray(U256.serializer(), bytes))
        }
    }

    // -- I256 --

    @Test
    fun `roundtrip I256`() {
        val values = listOf(
            I256.ZERO,
            I256(1u, 0u, 0u, 0u),
            I256(ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE), // -1
        )
        for (v in values) {
            val bytes = Bsatn.encodeToByteArray(I256.serializer(), v)
            assertEquals(v, Bsatn.decodeFromByteArray(I256.serializer(), bytes))
        }
    }

    // -- Compatibility with Rust BSATN --
    // These test against known byte sequences that the Rust encoder produces.

    @Test
    fun `U128 encoding matches Rust`() {
        // Rust: bsatn::to_vec(&1u128) produces 16 bytes:
        // [1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
        val bytes = Bsatn.encodeToByteArray(U128.serializer(), U128(1u, 0u))
        val expected = ByteArray(16).also { it[0] = 1 }
        assertContentEquals(expected, bytes)
    }

    @Test
    fun `U256 encoding matches Rust`() {
        // Rust: bsatn::to_vec(&1u256) produces 32 bytes:
        // [1, 0, 0, ...0]
        val bytes = Bsatn.encodeToByteArray(U256.serializer(), U256(1u, 0u, 0u, 0u))
        val expected = ByteArray(32).also { it[0] = 1 }
        assertContentEquals(expected, bytes)
    }
}
