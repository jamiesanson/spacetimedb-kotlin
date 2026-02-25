package dev.sanson.spacetimedb.bsatn

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class BsatnCompositeTest {

    // -- Data class (product type) --

    @Serializable
    data class Point(val x: Int, val y: Int)

    @Test
    fun `encode product type`() {
        val bytes = Bsatn.encodeToByteArray(Point.serializer(), Point(1, 2))
        // x=1 as i32LE + y=2 as i32LE, no prefix
        assertContentEquals(
            byteArrayOf(
                0x01, 0x00, 0x00, 0x00,
                0x02, 0x00, 0x00, 0x00,
            ),
            bytes,
        )
    }

    @Test
    fun `roundtrip product type`() {
        val value = Point(42, -100)
        val bytes = Bsatn.encodeToByteArray(Point.serializer(), value)
        assertEquals(value, Bsatn.decodeFromByteArray(Point.serializer(), bytes))
    }

    // -- Nested products --

    @Serializable
    data class Line(val start: Point, val end: Point)

    @Test
    fun `roundtrip nested product`() {
        val value = Line(Point(0, 0), Point(10, 20))
        val bytes = Bsatn.encodeToByteArray(Line.serializer(), value)
        assertEquals(value, Bsatn.decodeFromByteArray(Line.serializer(), bytes))
    }

    // -- List --

    @Test
    fun `encode list with count prefix`() {
        val list = listOf(1, 2, 3)
        val bytes = Bsatn.encodeToByteArray(ListSerializer(Int.serializer()), list)
        // u32LE(3) + 3x i32LE
        assertContentEquals(
            byteArrayOf(
                0x03, 0x00, 0x00, 0x00, // count = 3
                0x01, 0x00, 0x00, 0x00, // 1
                0x02, 0x00, 0x00, 0x00, // 2
                0x03, 0x00, 0x00, 0x00, // 3
            ),
            bytes,
        )
    }

    @Test
    fun `roundtrip empty list`() {
        val list = emptyList<Int>()
        val bytes = Bsatn.encodeToByteArray(ListSerializer(Int.serializer()), list)
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), bytes)
        assertEquals(list, Bsatn.decodeFromByteArray(ListSerializer(Int.serializer()), bytes))
    }

    @Test
    fun `roundtrip list of strings`() {
        val list = listOf("hello", "world", "")
        val bytes = Bsatn.encodeToByteArray(ListSerializer(String.serializer()), list)
        assertEquals(list, Bsatn.decodeFromByteArray(ListSerializer(String.serializer()), bytes))
    }

    @Test
    fun `roundtrip list of products`() {
        val list = listOf(Point(1, 2), Point(3, 4), Point(5, 6))
        val bytes = Bsatn.encodeToByteArray(ListSerializer(Point.serializer()), list)
        assertEquals(list, Bsatn.decodeFromByteArray(ListSerializer(Point.serializer()), bytes))
    }

    // -- Nullable (BSATN sum: tag 0 = Some, tag 1 = None) --

    @Test
    fun `encode nullable some`() {
        val bytes = Bsatn.encodeToByteArray(Int.serializer().nullable, 42)
        // tag=0 (Some) + i32LE(42)
        assertContentEquals(
            byteArrayOf(0x00, 0x2A, 0x00, 0x00, 0x00),
            bytes,
        )
    }

    @Test
    fun `encode nullable none`() {
        val bytes = Bsatn.encodeToByteArray(Int.serializer().nullable, null)
        // tag=1 (None)
        assertContentEquals(byteArrayOf(0x01), bytes)
    }

    @Test
    fun `roundtrip nullable some`() {
        val value: Int? = 42
        val bytes = Bsatn.encodeToByteArray(Int.serializer().nullable, value)
        assertEquals(value, Bsatn.decodeFromByteArray(Int.serializer().nullable, bytes))
    }

    @Test
    fun `roundtrip nullable none`() {
        val value: Int? = null
        val bytes = Bsatn.encodeToByteArray(Int.serializer().nullable, value)
        assertNull(Bsatn.decodeFromByteArray(Int.serializer().nullable, bytes))
    }

    // -- Sealed class (sum type) --

    @Serializable
    sealed class Shape {
        @Serializable
        data class Circle(val radius: Float) : Shape()

        @Serializable
        data class Rect(val width: Float, val height: Float) : Shape()
    }

    @Test
    fun `encode sealed class variant 0`() {
        val bytes = Bsatn.encodeToByteArray(Shape.serializer(), Shape.Circle(1.5f))
        // tag=0 + f32(1.5) as bits LE
        val expected = byteArrayOf(0x00) + Bsatn.encodeToByteArray(Float.serializer(), 1.5f)
        assertContentEquals(expected, bytes)
    }

    @Test
    fun `encode sealed class variant 1`() {
        val bytes = Bsatn.encodeToByteArray(Shape.serializer(), Shape.Rect(3.0f, 4.0f))
        // tag=1 + f32(3.0) + f32(4.0)
        val expected =
            byteArrayOf(0x01) +
                Bsatn.encodeToByteArray(Float.serializer(), 3.0f) +
                Bsatn.encodeToByteArray(Float.serializer(), 4.0f)
        assertContentEquals(expected, bytes)
    }

    @Test
    fun `roundtrip sealed class`() {
        val shapes = listOf(Shape.Circle(2.5f), Shape.Rect(10.0f, 20.0f))
        for (shape in shapes) {
            val bytes = Bsatn.encodeToByteArray(Shape.serializer(), shape)
            assertEquals(shape, Bsatn.decodeFromByteArray(Shape.serializer(), bytes))
        }
    }

    @Test
    fun `invalid sealed class tag throws`() {
        // Shape only has 2 variants (0, 1) — tag 5 is invalid
        val bytes = byteArrayOf(0x05, 0x00, 0x00, 0x00, 0x00)
        assertFailsWith<BsatnDecodeException.InvalidTag> {
            Bsatn.decodeFromByteArray(Shape.serializer(), bytes)
        }
    }

    // -- Enum (simple, no payload) --

    @Serializable
    enum class Color { Red, Green, Blue }

    @Test
    fun `encode enum as u8 tag`() {
        assertContentEquals(byteArrayOf(0), Bsatn.encodeToByteArray(Color.serializer(), Color.Red))
        assertContentEquals(byteArrayOf(1), Bsatn.encodeToByteArray(Color.serializer(), Color.Green))
        assertContentEquals(byteArrayOf(2), Bsatn.encodeToByteArray(Color.serializer(), Color.Blue))
    }

    @Test
    fun `roundtrip enum`() {
        for (c in Color.entries) {
            val bytes = Bsatn.encodeToByteArray(Color.serializer(), c)
            assertEquals(c, Bsatn.decodeFromByteArray(Color.serializer(), bytes))
        }
    }

    // -- Product with various field types --

    @Serializable
    data class MixedRow(
        val id: Int,
        val name: String,
        val score: Double,
        val active: Boolean,
        val tags: List<String>,
        val metadata: Point?,
    )

    @Test
    fun `roundtrip complex product`() {
        val value =
            MixedRow(
                id = 1,
                name = "Alice",
                score = 99.5,
                active = true,
                tags = listOf("admin", "user"),
                metadata = Point(10, 20),
            )
        val bytes = Bsatn.encodeToByteArray(MixedRow.serializer(), value)
        assertEquals(value, Bsatn.decodeFromByteArray(MixedRow.serializer(), bytes))
    }

    @Test
    fun `roundtrip complex product with null`() {
        val value =
            MixedRow(
                id = 2,
                name = "",
                score = 0.0,
                active = false,
                tags = emptyList(),
                metadata = null,
            )
        val bytes = Bsatn.encodeToByteArray(MixedRow.serializer(), value)
        assertEquals(value, Bsatn.decodeFromByteArray(MixedRow.serializer(), bytes))
    }

    // -- Map rejection --

    @Test
    fun `encoding map throws`() {
        assertFailsWith<BsatnEncodeException> {
            Bsatn.encodeToByteArray(
                MapSerializer(String.serializer(), Int.serializer()),
                mapOf("a" to 1),
            )
        }
    }
}
