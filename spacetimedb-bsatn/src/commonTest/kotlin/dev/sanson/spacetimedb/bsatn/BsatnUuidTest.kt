package dev.sanson.spacetimedb.bsatn

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@OptIn(ExperimentalUuidApi::class)
class BsatnUuidTest {

    @Test
    fun `Uuid roundtrip`() {
        val uuid = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
        val bytes = Bsatn.encodeToByteArray(UuidSerializer, uuid)
        val decoded = Bsatn.decodeFromByteArray(UuidSerializer, bytes)
        assertEquals(uuid, decoded)
    }

    @Test
    fun `Uuid serializes as 16 LE bytes`() {
        val uuid = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
        val bytes = Bsatn.encodeToByteArray(UuidSerializer, uuid)
        assertEquals(16, bytes.size)
    }

    @Test
    fun `Uuid nil is all zeros`() {
        val nil = Uuid.fromLongs(0L, 0L)
        val bytes = Bsatn.encodeToByteArray(UuidSerializer, nil)
        assertContentEquals(ByteArray(16), bytes)
    }

    @Test
    fun `Uuid byte layout matches U128 LE`() {
        // UUID with known byte pattern: MSB = 0x0102030405060708 and LSB = 0x090a0b0c0d0e0f10
        val uuid = Uuid.fromLongs(
            mostSignificantBits = 0x0102030405060708L,
            leastSignificantBits = 0x090a0b0c0d0e0f10L,
        )
        val uuidBytes = Bsatn.encodeToByteArray(UuidSerializer, uuid)

        // Equivalent U128: lo = LSB and hi = MSB
        val u128 = U128(lo = 0x090a0b0c0d0e0f10u, hi = 0x0102030405060708u)
        val u128Bytes = Bsatn.encodeToByteArray(U128.serializer(), u128)

        assertContentEquals(u128Bytes, uuidBytes)
    }

    @Test
    fun `Uuid in struct roundtrip`() {
        @Serializable
        data class Row(
            @Serializable(with = UuidSerializer::class)
            val id: Uuid,
            val name: String,
        )

        val row = Row(
            id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
            name = "test",
        )
        val bytes = Bsatn.encodeToByteArray(Row.serializer(), row)
        val decoded = Bsatn.decodeFromByteArray(Row.serializer(), bytes)
        assertEquals(row, decoded)
    }

    @Test
    fun `Uuid random roundtrip`() {
        val uuid = Uuid.random()
        val bytes = Bsatn.encodeToByteArray(UuidSerializer, uuid)
        val decoded = Bsatn.decodeFromByteArray(UuidSerializer, bytes)
        assertEquals(uuid, decoded)
    }
}
