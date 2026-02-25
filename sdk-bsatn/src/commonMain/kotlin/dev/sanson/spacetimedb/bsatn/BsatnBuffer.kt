package dev.sanson.spacetimedb.bsatn

/**
 * A growable byte buffer for BSATN encoding.
 */
internal class BsatnBuffer {
    private var data = ByteArray(256)
    private var position = 0

    private fun ensureCapacity(needed: Int) {
        val required = position + needed
        if (required > data.size) {
            var newSize = data.size * 2
            while (newSize < required) newSize *= 2
            data = data.copyOf(newSize)
        }
    }

    fun writeByte(value: Int) {
        ensureCapacity(1)
        data[position++] = value.toByte()
    }

    fun writeShortLE(value: Short) {
        ensureCapacity(2)
        val v = value.toInt()
        data[position++] = (v and 0xFF).toByte()
        data[position++] = ((v shr 8) and 0xFF).toByte()
    }

    fun writeIntLE(value: Int) {
        ensureCapacity(4)
        data[position++] = (value and 0xFF).toByte()
        data[position++] = ((value shr 8) and 0xFF).toByte()
        data[position++] = ((value shr 16) and 0xFF).toByte()
        data[position++] = ((value shr 24) and 0xFF).toByte()
    }

    fun writeLongLE(value: Long) {
        ensureCapacity(8)
        data[position++] = (value and 0xFF).toByte()
        data[position++] = ((value shr 8) and 0xFF).toByte()
        data[position++] = ((value shr 16) and 0xFF).toByte()
        data[position++] = ((value shr 24) and 0xFF).toByte()
        data[position++] = ((value shr 32) and 0xFF).toByte()
        data[position++] = ((value shr 40) and 0xFF).toByte()
        data[position++] = ((value shr 48) and 0xFF).toByte()
        data[position++] = ((value shr 56) and 0xFF).toByte()
    }

    fun writeBytes(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        bytes.copyInto(data, position)
        position += bytes.size
    }

    fun toByteArray(): ByteArray = data.copyOf(position)
}

/**
 * A sequential reader over a byte array for BSATN decoding.
 */
internal class BsatnReader(private val data: ByteArray) {
    private var position = 0

    private fun checkAvailable(needed: Int) {
        if (position + needed > data.size) {
            throw BsatnDecodeException(
                "Unexpected end of input: need $needed bytes at position $position, but only ${data.size - position} available",
            )
        }
    }

    fun readByte(): Int {
        checkAvailable(1)
        return data[position++].toInt() and 0xFF
    }

    fun readShortLE(): Short {
        checkAvailable(2)
        val b0 = data[position++].toInt() and 0xFF
        val b1 = data[position++].toInt() and 0xFF
        return (b0 or (b1 shl 8)).toShort()
    }

    fun readIntLE(): Int {
        checkAvailable(4)
        val b0 = data[position++].toInt() and 0xFF
        val b1 = data[position++].toInt() and 0xFF
        val b2 = data[position++].toInt() and 0xFF
        val b3 = data[position++].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    fun readLongLE(): Long {
        checkAvailable(8)
        val b0 = data[position++].toLong() and 0xFF
        val b1 = data[position++].toLong() and 0xFF
        val b2 = data[position++].toLong() and 0xFF
        val b3 = data[position++].toLong() and 0xFF
        val b4 = data[position++].toLong() and 0xFF
        val b5 = data[position++].toLong() and 0xFF
        val b6 = data[position++].toLong() and 0xFF
        val b7 = data[position++].toLong() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24) or
            (b4 shl 32) or (b5 shl 40) or (b6 shl 48) or (b7 shl 56)
    }

    fun readBytes(count: Int): ByteArray {
        checkAvailable(count)
        val result = data.copyOfRange(position, position + count)
        position += count
        return result
    }

    val remaining: Int get() = data.size - position
}

/**
 * Exception thrown when BSATN decoding fails.
 */
public class BsatnDecodeException(message: String) : RuntimeException(message)
