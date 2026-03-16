package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.protocol.ClientMessage
import kotlinx.coroutines.channels.SendChannel
import org.intellij.lang.annotations.Language

/**
 * Builds a subscription to one or more SQL queries.
 *
 * Obtain via [SpacetimeDbConnection.subscriptionBuilder].
 */
public class SubscriptionBuilder
internal constructor(
    private val sendChannel: SendChannel<ClientMessage>,
    private val registerHandle: (SubscriptionHandle) -> Unit,
) {
    private var onApplied: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    /** Callback invoked once the server applies this subscription. */
    public fun onApplied(callback: () -> Unit): SubscriptionBuilder = apply { onApplied = callback }

    /** Callback invoked if the subscription encounters an error. */
    public fun onError(callback: (String) -> Unit): SubscriptionBuilder = apply {
        onError = callback
    }

    /**
     * Subscribe to the given SQL queries.
     *
     * The returned [SubscriptionHandle] can be used to track the subscription state and to
     * unsubscribe later.
     */
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    public fun subscribe(@Language("SQL") vararg queries: String): SubscriptionHandle {
        return subscribe(queries.toList())
    }

    /**
     * Subscribe to the given SQL queries.
     *
     * The returned [SubscriptionHandle] can be used to track the subscription state and to
     * unsubscribe later.
     */
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    public fun subscribe(queries: List<String>): SubscriptionHandle {
        require(queries.isNotEmpty()) { "At least one query is required" }
        val querySetId = nextQuerySetId()
        val handle =
            SubscriptionHandle(
                querySetId = querySetId,
                querySql = queries,
                sendChannel = sendChannel,
                onApplied = onApplied,
                onError = onError,
            )
        registerHandle(handle)
        // Queue the Subscribe message immediately
        val msg =
            ClientMessage.Subscribe(
                requestId = nextRequestId(),
                querySetId = querySetId,
                queryStrings = queries,
            )
        sendChannel.trySend(msg)
        return handle
    }
}
