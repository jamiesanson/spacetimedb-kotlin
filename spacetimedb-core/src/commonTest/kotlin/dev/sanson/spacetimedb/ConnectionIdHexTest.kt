package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.bsatn.U128
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConnectionIdHexTest {
    @Test
    fun `toHex produces 32-char lowercase hex`() {
        val id = ConnectionId(U128(lo = 0xABCD_EF01_2345_6789uL, hi = 0x0123_4567_89AB_CDEFuL))
        assertEquals("0123456789abcdefabcdef0123456789", id.toHex())
    }

    @Test
    fun `toHex of ZERO is 32 zeros`() {
        assertEquals("00000000000000000000000000000000", ConnectionId.ZERO.toHex())
    }

    @Test
    fun `fromHex round-trips with toHex`() {
        val hex = "0123456789abcdef0123456789abcdef"
        val id = ConnectionId.fromHex(hex)
        assertEquals(hex, id.toHex())
    }

    @Test
    fun `fromHex rejects wrong length`() {
        assertFailsWith<IllegalArgumentException> { ConnectionId.fromHex("abcd") }
    }

    @Test
    fun `fromHex parses big-endian correctly`() {
        val id = ConnectionId.fromHex("00000000000000010000000000000002")
        assertEquals(1uL, id.value.hi)
        assertEquals(2uL, id.value.lo)
    }
}
