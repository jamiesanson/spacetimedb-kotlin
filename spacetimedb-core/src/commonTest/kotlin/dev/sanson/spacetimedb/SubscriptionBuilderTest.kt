package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.protocol.ClientMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.channels.Channel

class SubscriptionBuilderTest {

    @Test
    fun `subscribe creates handle with correct queries`() {
        val channel = Channel<ClientMessage>(Channel.UNLIMITED)
        val handles = mutableListOf<SubscriptionHandle>()

        val builder =
            SubscriptionBuilder(sendChannel = channel, registerHandle = { handles.add(it) })

        val handle =
            builder
                .onApplied {}
                .onError {}
                .subscribe("SELECT * FROM users", "SELECT * FROM messages")

        assertNotNull(handle)
        assertEquals(listOf("SELECT * FROM users", "SELECT * FROM messages"), handle.querySql)
        assertEquals(1, handles.size)
        assertEquals(handle, handles.first())
    }

    @Test
    fun `subscribe with list creates handle`() {
        val channel = Channel<ClientMessage>(Channel.UNLIMITED)
        val handles = mutableListOf<SubscriptionHandle>()

        val builder =
            SubscriptionBuilder(sendChannel = channel, registerHandle = { handles.add(it) })

        val queries = listOf("SELECT * FROM users")
        val handle = builder.subscribe(queries)
        assertNotNull(handle)
        assertEquals(queries, handle.querySql)
    }

    @Test
    fun `subscribe requires at least one query`() {
        val channel = Channel<ClientMessage>(Channel.UNLIMITED)

        val builder = SubscriptionBuilder(sendChannel = channel, registerHandle = {})

        assertFailsWith<IllegalArgumentException> { builder.subscribe(emptyList()) }
    }
}
