package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.bsatn.U256
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * A unique identifier for a user, derived from their credentials.
 *
 * Wraps a 256-bit unsigned integer, serialized as 32 little-endian bytes in BSATN.
 */
@Serializable
@JvmInline
public value class Identity(public val value: U256) : Comparable<Identity> {
    public companion object {
        public val ZERO: Identity = Identity(U256.ZERO)
    }

    override fun compareTo(other: Identity): Int = value.compareTo(other.value)

    /** Returns the identity as a 64-character lowercase hex string (big-endian convention). */
    public fun toHexString(): String =
        buildString(64) {
            // Big-endian: most-significant word first
            append(value.w3.toString(16).padStart(16, '0'))
            append(value.w2.toString(16).padStart(16, '0'))
            append(value.w1.toString(16).padStart(16, '0'))
            append(value.w0.toString(16).padStart(16, '0'))
        }

    override fun toString(): String = "Identity(${toHexString()})"
}
