package dev.sanson.spacetimedb.bsatn

import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BsatnPrimitiveTest {

    // -- Boolean --

    @Test
    fun `encode true`() {
        val bytes = Bsatn.encodeToByteArray(Boolean.serializer(), true)
        assertContentEquals(byteArrayOf(1), bytes)
    }

    @Test
    fun `encode false`() {
        val bytes = Bsatn.encodeToByteArray(Boolean.serializer(), false)
        assertContentEquals(byteArrayOf(0), bytes)
    }

    @Test
    fun `roundtrip boolean`() {
        for (v in listOf(true, false)) {
            val bytes = Bsatn.encodeToByteArray(Boolean.serializer(), v)
            assertEquals(v, Bsatn.decodeFromByteArray(Boolean.serializer(), bytes))
        }
    }

    @Test
    fun `invalid bool byte throws`() {
        // Rust rejects any byte other than 0 or 1 as a bool
        for (b in listOf(2, 127, 255)) {
            assertFailsWith<BsatnDecodeException> {
                Bsatn.decodeFromByteArray(Boolean.serializer(), byteArrayOf(b.toByte()))
            }
        }
    }

    // -- Byte --

    @Test
    fun `roundtrip byte`() {
        for (v in listOf(Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE)) {
            val bytes = Bsatn.encodeToByteArray(Byte.serializer(), v)
            assertEquals(1, bytes.size)
            assertEquals(v, Bsatn.decodeFromByteArray(Byte.serializer(), bytes))
        }
    }

    // -- Short (i16 LE) --

    @Test
    fun `encode short little-endian`() {
        // 0x0102 = 258 → LE bytes: [0x02, 0x01]
        val bytes = Bsatn.encodeToByteArray(Short.serializer(), 258.toShort())
        assertContentEquals(byteArrayOf(0x02, 0x01), bytes)
    }

    @Test
    fun `roundtrip short`() {
        for (v in listOf(Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE)) {
            val bytes = Bsatn.encodeToByteArray(Short.serializer(), v)
            assertEquals(2, bytes.size)
            assertEquals(v, Bsatn.decodeFromByteArray(Short.serializer(), bytes))
        }
    }

    // -- Int (i32 LE) --

    @Test
    fun `encode int little-endian`() {
        // 0x01020304 → LE: [0x04, 0x03, 0x02, 0x01]
        val bytes = Bsatn.encodeToByteArray(Int.serializer(), 0x01020304)
        assertContentEquals(byteArrayOf(0x04, 0x03, 0x02, 0x01), bytes)
    }

    @Test
    fun `roundtrip int`() {
        for (v in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            val bytes = Bsatn.encodeToByteArray(Int.serializer(), v)
            assertEquals(4, bytes.size)
            assertEquals(v, Bsatn.decodeFromByteArray(Int.serializer(), bytes))
        }
    }

    // -- Long (i64 LE) --

    @Test
    fun `encode long little-endian`() {
        val bytes = Bsatn.encodeToByteArray(Long.serializer(), 0x0102030405060708L)
        assertContentEquals(byteArrayOf(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01), bytes)
    }

    @Test
    fun `roundtrip long`() {
        for (v in listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)) {
            val bytes = Bsatn.encodeToByteArray(Long.serializer(), v)
            assertEquals(8, bytes.size)
            assertEquals(v, Bsatn.decodeFromByteArray(Long.serializer(), bytes))
        }
    }

    // -- Float (f32 as bits LE) --

    @Test
    fun `roundtrip float`() {
        for (v in listOf(0.0f, 1.0f, -1.0f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN)) {
            val bytes = Bsatn.encodeToByteArray(Float.serializer(), v)
            assertEquals(4, bytes.size)
            val decoded = Bsatn.decodeFromByteArray(Float.serializer(), bytes)
            // Compare by bit pattern to handle JS float precision differences
            assertEquals(v.toBits(), decoded.toBits())
        }
    }

    // -- Double (f64 as bits LE) --

    @Test
    fun `roundtrip double`() {
        for (v in listOf(0.0, 1.0, -1.0, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN)) {
            val bytes = Bsatn.encodeToByteArray(Double.serializer(), v)
            assertEquals(8, bytes.size)
            val decoded = Bsatn.decodeFromByteArray(Double.serializer(), bytes)
            if (v.isNaN()) {
                assertEquals(true, decoded.isNaN())
            } else {
                assertEquals(v, decoded)
            }
        }
    }

    // -- String --

    @Test
    fun `encode string with length prefix`() {
        val bytes = Bsatn.encodeToByteArray(String.serializer(), "hi")
        // u32LE(2) + "hi"
        assertContentEquals(byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x68, 0x69), bytes)
    }

    @Test
    fun `roundtrip empty string`() {
        val bytes = Bsatn.encodeToByteArray(String.serializer(), "")
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), bytes)
        assertEquals("", Bsatn.decodeFromByteArray(String.serializer(), bytes))
    }

    @Test
    fun `roundtrip string`() {
        for (v in listOf("", "hello", "hello world 🌍", "a".repeat(1000))) {
            val bytes = Bsatn.encodeToByteArray(String.serializer(), v)
            assertEquals(v, Bsatn.decodeFromByteArray(String.serializer(), bytes))
        }
    }

    // -- Truncated input --

    @Test
    fun `decode from truncated input throws`() {
        assertFailsWith<BsatnDecodeException> {
            // Need 4 bytes for an int, only provide 2
            Bsatn.decodeFromByteArray(Int.serializer(), byteArrayOf(0x01, 0x02))
        }
    }

    // -- Unsigned integers --

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun `encode UByte`() {
        val bytes = Bsatn.encodeToByteArray(UByte.serializer(), 255u.toUByte())
        assertContentEquals(byteArrayOf(0xFF.toByte()), bytes)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun `roundtrip UShort`() {
        for (v in listOf(UShort.MIN_VALUE, 1u.toUShort(), UShort.MAX_VALUE)) {
            val bytes = Bsatn.encodeToByteArray(UShort.serializer(), v)
            assertEquals(2, bytes.size)
            assertEquals(v, Bsatn.decodeFromByteArray(UShort.serializer(), bytes))
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun `roundtrip UInt`() {
        for (v in listOf(UInt.MIN_VALUE, 1u, UInt.MAX_VALUE)) {
            val bytes = Bsatn.encodeToByteArray(UInt.serializer(), v)
            assertEquals(4, bytes.size)
            assertEquals(v, Bsatn.decodeFromByteArray(UInt.serializer(), bytes))
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun `roundtrip ULong`() {
        for (v in listOf(ULong.MIN_VALUE, 1uL, ULong.MAX_VALUE)) {
            val bytes = Bsatn.encodeToByteArray(ULong.serializer(), v)
            assertEquals(8, bytes.size)
            assertEquals(v, Bsatn.decodeFromByteArray(ULong.serializer(), bytes))
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun `UInt MAX_VALUE encoding matches Rust u32 MAX`() {
        // Rust u32::MAX = 0xFFFFFFFF → LE: [0xFF, 0xFF, 0xFF, 0xFF]
        val bytes = Bsatn.encodeToByteArray(UInt.serializer(), UInt.MAX_VALUE)
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), bytes)
    }

    // -- ByteArray --

    @Test
    fun `encode byte array with length prefix`() {
        val bytes = Bsatn.encodeToByteArray(ByteArraySerializer(), byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
        // u32LE(2) + raw bytes
        assertContentEquals(byteArrayOf(0x02, 0x00, 0x00, 0x00, 0xCA.toByte(), 0xFE.toByte()), bytes)
    }

    @Test
    fun `roundtrip empty byte array`() {
        val value = byteArrayOf()
        val bytes = Bsatn.encodeToByteArray(ByteArraySerializer(), value)
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), bytes)
        assertContentEquals(value, Bsatn.decodeFromByteArray(ByteArraySerializer(), bytes))
    }

    @Test
    fun `roundtrip byte array`() {
        val value = ByteArray(256) { it.toByte() }
        val bytes = Bsatn.encodeToByteArray(ByteArraySerializer(), value)
        assertContentEquals(value, Bsatn.decodeFromByteArray(ByteArraySerializer(), bytes))
    }
}
