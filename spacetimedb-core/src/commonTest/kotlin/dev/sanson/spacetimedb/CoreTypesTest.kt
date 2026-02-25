package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.bsatn.Bsatn
import dev.sanson.spacetimedb.bsatn.U128
import dev.sanson.spacetimedb.bsatn.U256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CoreTypesTest {

    // -- Identity --

    @Test
    fun `Identity roundtrip`() {
        val id = Identity(U256(1u, 2u, 3u, 4u))
        val bytes = Bsatn.encodeToByteArray(Identity.serializer(), id)
        val decoded = Bsatn.decodeFromByteArray(Identity.serializer(), bytes)
        assertEquals(id, decoded)
    }

    @Test
    fun `Identity serializes as 32 LE bytes`() {
        val id = Identity(U256(0x0102030405060708u, 0u, 0u, 0u))
        val bytes = Bsatn.encodeToByteArray(Identity.serializer(), id)
        assertEquals(32, bytes.size)
        // First 8 bytes are w0 in little-endian
        assertEquals(0x08.toByte(), bytes[0])
        assertEquals(0x07.toByte(), bytes[1])
        assertEquals(0x06.toByte(), bytes[2])
        assertEquals(0x05.toByte(), bytes[3])
    }

    @Test
    fun `Identity ZERO`() {
        val bytes = Bsatn.encodeToByteArray(Identity.serializer(), Identity.ZERO)
        assertContentEquals(ByteArray(32), bytes)
    }

    @Test
    fun `Identity toHexString`() {
        // All zeros
        assertEquals(
            "0000000000000000000000000000000000000000000000000000000000000000",
            Identity.ZERO.toHexString(),
        )

        // Simple case: w0 = 1, rest = 0
        val id = Identity(U256(1u, 0u, 0u, 0u))
        val hex = id.toHexString()
        assertEquals(64, hex.length)
        assertEquals("0000000000000001", hex.substring(48))
    }

    // -- ConnectionId --

    @Test
    fun `ConnectionId roundtrip`() {
        val cid = ConnectionId(U128(0xDEADBEEFu, 0xCAFEBABEu))
        val bytes = Bsatn.encodeToByteArray(ConnectionId.serializer(), cid)
        val decoded = Bsatn.decodeFromByteArray(ConnectionId.serializer(), bytes)
        assertEquals(cid, decoded)
    }

    @Test
    fun `ConnectionId serializes as 16 LE bytes`() {
        val cid = ConnectionId(U128(0x0102030405060708u, 0u))
        val bytes = Bsatn.encodeToByteArray(ConnectionId.serializer(), cid)
        assertEquals(16, bytes.size)
        assertEquals(0x08.toByte(), bytes[0])
        assertEquals(0x07.toByte(), bytes[1])
    }

    @Test
    fun `ConnectionId ZERO`() {
        val bytes = Bsatn.encodeToByteArray(ConnectionId.serializer(), ConnectionId.ZERO)
        assertContentEquals(ByteArray(16), bytes)
    }

    // -- Timestamp --

    @Test
    fun `Timestamp roundtrip`() {
        val ts = Timestamp(1_000_000L) // 1 second
        val bytes = Bsatn.encodeToByteArray(Timestamp.serializer(), ts)
        val decoded = Bsatn.decodeFromByteArray(Timestamp.serializer(), bytes)
        assertEquals(ts, decoded)
    }

    @Test
    fun `Timestamp serializes as 8 LE bytes`() {
        val ts = Timestamp(0x0102030405060708L)
        val bytes = Bsatn.encodeToByteArray(Timestamp.serializer(), ts)
        assertEquals(8, bytes.size)
        assertEquals(0x08.toByte(), bytes[0])
        assertEquals(0x07.toByte(), bytes[1])
    }

    @Test
    fun `Timestamp UNIX_EPOCH`() {
        val bytes = Bsatn.encodeToByteArray(Timestamp.serializer(), Timestamp.UNIX_EPOCH)
        assertContentEquals(ByteArray(8), bytes)
    }

    @Test
    fun `Timestamp negative value`() {
        val ts = Timestamp(-1_000_000L)
        val bytes = Bsatn.encodeToByteArray(Timestamp.serializer(), ts)
        val decoded = Bsatn.decodeFromByteArray(Timestamp.serializer(), bytes)
        assertEquals(ts, decoded)
    }

    // -- TimeDuration --

    @Test
    fun `TimeDuration roundtrip`() {
        val dur = TimeDuration(5_000_000L) // 5 seconds
        val bytes = Bsatn.encodeToByteArray(TimeDuration.serializer(), dur)
        val decoded = Bsatn.decodeFromByteArray(TimeDuration.serializer(), bytes)
        assertEquals(dur, decoded)
    }

    @Test
    fun `TimeDuration negative`() {
        val dur = TimeDuration(-1_000_000L)
        val bytes = Bsatn.encodeToByteArray(TimeDuration.serializer(), dur)
        val decoded = Bsatn.decodeFromByteArray(TimeDuration.serializer(), bytes)
        assertEquals(dur, decoded)
    }

    @Test
    fun `TimeDuration ZERO`() {
        val bytes = Bsatn.encodeToByteArray(TimeDuration.serializer(), TimeDuration.ZERO)
        assertContentEquals(ByteArray(8), bytes)
    }

    // -- ScheduleAt --

    @Test
    fun `ScheduleAt Interval roundtrip`() {
        val schedule = ScheduleAt.Interval(TimeDuration(60_000_000L))
        val bytes = Bsatn.encodeToByteArray(ScheduleAt.serializer(), schedule)
        val decoded = Bsatn.decodeFromByteArray(ScheduleAt.serializer(), bytes)
        assertEquals(schedule, decoded)
    }

    @Test
    fun `ScheduleAt Time roundtrip`() {
        val schedule = ScheduleAt.Time(Timestamp(1_700_000_000_000_000L))
        val bytes = Bsatn.encodeToByteArray(ScheduleAt.serializer(), schedule)
        val decoded = Bsatn.decodeFromByteArray(ScheduleAt.serializer(), bytes)
        assertEquals(schedule, decoded)
    }

    @Test
    fun `ScheduleAt Interval has tag 0`() {
        val schedule = ScheduleAt.Interval(TimeDuration(1L))
        val bytes = Bsatn.encodeToByteArray(ScheduleAt.serializer(), schedule)
        // First byte is the sum type tag
        assertEquals(0.toByte(), bytes[0])
    }

    @Test
    fun `ScheduleAt Time has tag 1`() {
        val schedule = ScheduleAt.Time(Timestamp(1L))
        val bytes = Bsatn.encodeToByteArray(ScheduleAt.serializer(), schedule)
        // First byte is the sum type tag
        assertEquals(1.toByte(), bytes[0])
    }

    // -- Composite in struct --

    @Test
    fun `Identity and Timestamp in a struct`() {
        @kotlinx.serialization.Serializable
        data class Event(val caller: Identity, val at: Timestamp)

        val event = Event(
            caller = Identity(U256(42u, 0u, 0u, 0u)),
            at = Timestamp(1_000_000L),
        )
        val bytes = Bsatn.encodeToByteArray(Event.serializer(), event)
        // 32 bytes identity + 8 bytes timestamp
        assertEquals(40, bytes.size)
        val decoded = Bsatn.decodeFromByteArray(Event.serializer(), bytes)
        assertEquals(event, decoded)
    }
}
