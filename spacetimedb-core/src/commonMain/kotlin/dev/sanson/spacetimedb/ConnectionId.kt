package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.bsatn.U128
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * A unique identifier for a client connection to a SpacetimeDB database.
 *
 * Wraps a 128-bit unsigned integer, serialized as 16 little-endian bytes in BSATN.
 */
@Serializable
@JvmInline
public value class ConnectionId(public val value: U128) : Comparable<ConnectionId> {
    public companion object {
        public val ZERO: ConnectionId = ConnectionId(U128.ZERO)

        /** Parse a [ConnectionId] from a 32-character hexadecimal string (big-endian). */
        public fun fromHex(hex: String): ConnectionId {
            require(hex.length == 32) {
                "ConnectionId hex must be 32 characters, got ${hex.length}"
            }
            val hi = hex.substring(0, 16).toULong(16)
            val lo = hex.substring(16, 32).toULong(16)
            return ConnectionId(U128(lo, hi))
        }
    }

    /** Converts this connection ID to a 32-character lowercase hexadecimal string (big-endian). */
    public fun toHex(): String {
        val hi = value.hi.toString(16).padStart(16, '0')
        val lo = value.lo.toString(16).padStart(16, '0')
        return hi + lo
    }

    override fun compareTo(other: ConnectionId): Int = value.compareTo(other.value)

    override fun toString(): String = "ConnectionId($value)"
}
