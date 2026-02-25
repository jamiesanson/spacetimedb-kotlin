package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.protocol.ClientMessage
import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionHandleTest {

    private fun createHandle(
        onApplied: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ): Pair<SubscriptionHandle, Channel<ClientMessage>> {
        val channel = Channel<ClientMessage>(Channel.UNLIMITED)
        val handle = SubscriptionHandle(
            querySetId = nextQuerySetId(),
            querySql = listOf("SELECT * FROM users"),
            sendChannel = channel,
            onApplied = onApplied,
            onError = onError,
        )
        return handle to channel
    }

    // -- State transitions --

    @Test
    fun `new handle is not active and not ended`() {
        val (handle, _) = createHandle()
        // Starts in Sent state
        assertFalse(handle.isActive)
        assertFalse(handle.isEnded)
    }

    @Test
    fun `notifyApplied returns callback and handle becomes active`() {
        var called = false
        val (handle, _) = createHandle(onApplied = { called = true })
        val callback = handle.notifyApplied()
        assertNotNull(callback)
        callback()
        assertTrue(called)
        assertTrue(handle.isActive)
        assertFalse(handle.isEnded)
    }

    @Test
    fun `notifyApplied returns null on second call`() {
        val (handle, _) = createHandle(onApplied = { })
        handle.notifyApplied()
        val callback = handle.notifyApplied()
        assertNull(callback)
    }

    @Test
    fun `notifyEnded moves handle to ended`() {
        val (handle, _) = createHandle()
        handle.notifyApplied()
        handle.notifyEnded()
        assertTrue(handle.isEnded)
        assertFalse(handle.isActive)
    }

    @Test
    fun `notifyError moves handle to ended`() {
        var errorMsg: String? = null
        val (handle, _) = createHandle(onError = { errorMsg = it })
        val callback = handle.notifyError("query failed")
        assertNotNull(callback)
        callback()
        assertEquals("query failed", errorMsg)
        assertTrue(handle.isEnded)
    }

    // -- Unsubscribe --

    @Test
    fun `unsubscribe on applied handle sends Unsubscribe message`() {
        val (handle, channel) = createHandle()
        handle.notifyApplied()
        handle.unsubscribe()
        val msg = channel.tryReceive().getOrNull()
        assertNotNull(msg)
        assertIs<ClientMessage.Unsubscribe>(msg)
        assertEquals(handle.querySetId, msg.querySetId)
    }

    @Test
    fun `unsubscribe before applied does not send message`() {
        val (handle, channel) = createHandle()
        // Still in Sent state
        handle.unsubscribe()
        val msg = channel.tryReceive().getOrNull()
        assertNull(msg)
    }

    @Test
    fun `double unsubscribe is a no-op`() {
        val (handle, channel) = createHandle()
        handle.notifyApplied()
        handle.unsubscribe()
        channel.tryReceive() // drain the first message
        handle.unsubscribe()
        val msg = channel.tryReceive().getOrNull()
        assertNull(msg)
    }
}
