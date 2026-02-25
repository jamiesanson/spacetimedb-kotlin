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
    }

    override fun compareTo(other: ConnectionId): Int = value.compareTo(other.value)

    override fun toString(): String = "ConnectionId($value)"
}
