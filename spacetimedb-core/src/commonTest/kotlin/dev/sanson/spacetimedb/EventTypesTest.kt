package dev.sanson.spacetimedb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EventTypesTest {

    // -- Status --

    @Test
    fun `Status Committed is a singleton`() {
        assertIs<Status.Committed>(Status.Committed)
    }

    @Test
    fun `Status Failed carries message`() {
        val status = Status.Failed("reducer panicked")
        assertEquals("reducer panicked", status.message)
    }

    @Test
    fun `Status Panic carries message`() {
        val status = Status.Panic("out of energy")
        assertEquals("out of energy", status.message)
    }

    // -- ReducerEvent --

    @Test
    fun `ReducerEvent carries timestamp and status and reducer`() {
        val event =
            ReducerEvent(
                timestamp = Timestamp.fromEpochMicroseconds(1_000_000L),
                status = Status.Committed,
                reducer = "add_player",
            )
        assertEquals(Timestamp.fromEpochMicroseconds(1_000_000L), event.timestamp)
        assertIs<Status.Committed>(event.status)
        assertEquals("add_player", event.reducer)
    }

    // -- Event --

    @Test
    fun `Event Reducer wraps ReducerEvent`() {
        val reducerEvent =
            ReducerEvent(
                timestamp = Timestamp.fromEpochMicroseconds(1L),
                status = Status.Committed,
                reducer = 42,
            )
        val event: Event<Int> = Event.Reducer(reducerEvent)
        assertIs<Event.Reducer<Int>>(event)
        assertEquals(reducerEvent, event.event)
    }

    @Test
    fun `Event SubscribeApplied`() {
        val event: Event<Nothing> = Event.SubscribeApplied
        assertIs<Event.SubscribeApplied>(event)
    }

    @Test
    fun `Event UnsubscribeApplied`() {
        val event: Event<Nothing> = Event.UnsubscribeApplied
        assertIs<Event.UnsubscribeApplied>(event)
    }

    @Test
    fun `Event Disconnected`() {
        val event: Event<Nothing> = Event.Disconnected
        assertIs<Event.Disconnected>(event)
    }

    @Test
    fun `Event SubscribeError wraps SpacetimeError`() {
        val error = SpacetimeError.SubscriptionError("invalid SQL")
        val event: Event<Nothing> = Event.SubscribeError(error)
        assertIs<Event.SubscribeError>(event)
        assertEquals(error, event.error)
    }

    @Test
    fun `Event Transaction`() {
        val event: Event<Nothing> = Event.Transaction
        assertIs<Event.Transaction>(event)
    }

    @Test
    fun `Event is covariant in R`() {
        val intEvent: Event<Int> =
            Event.Reducer(ReducerEvent(Timestamp.fromEpochMicroseconds(0L), Status.Committed, 42))
        // Should compile: Event<Int> assignable to Event<Any>
        val anyEvent: Event<Any> = intEvent
        assertIs<Event.Reducer<Any>>(anyEvent)
    }
}

class SpacetimeErrorTest {

    @Test
    fun `Disconnected error message`() {
        val error = SpacetimeError.Disconnected
        assertEquals("Connection is already disconnected or has terminated normally", error.message)
    }

    @Test
    fun `FailedToConnect with cause`() {
        val cause = RuntimeException("timeout")
        val error = SpacetimeError.FailedToConnect(cause)
        assertEquals("Failed to connect", error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `FailedToConnect without cause`() {
        val error = SpacetimeError.FailedToConnect()
        assertNull(error.cause)
    }

    @Test
    fun `SubscriptionError carries error string`() {
        val error = SpacetimeError.SubscriptionError("invalid query")
        assertIs<SpacetimeError.SubscriptionError>(error)
        assertEquals("invalid query", error.error)
    }

    @Test
    fun `AlreadyEnded`() {
        val error = SpacetimeError.AlreadyEnded
        assertEquals("Subscription has already ended", error.message)
    }

    @Test
    fun `AlreadyUnsubscribed`() {
        val error = SpacetimeError.AlreadyUnsubscribed
        assertEquals("Unsubscribe already called on subscription", error.message)
    }

    @Test
    fun `Internal with message`() {
        val error = SpacetimeError.Internal("something broke")
        assertEquals("something broke", error.message)
    }

    @Test
    fun `Internal with cause`() {
        val cause = IllegalStateException("bad state")
        val error = SpacetimeError.Internal("wrapper", cause)
        assertEquals("wrapper", error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `all errors are SpacetimeError`() {
        val errors: List<SpacetimeError> =
            listOf(
                SpacetimeError.Disconnected,
                SpacetimeError.FailedToConnect(),
                SpacetimeError.SubscriptionError("err"),
                SpacetimeError.AlreadyEnded,
                SpacetimeError.AlreadyUnsubscribed,
                SpacetimeError.Internal("msg"),
            )
        assertEquals(6, errors.size)
        errors.forEach { assertIs<SpacetimeError>(it) }
    }
}
