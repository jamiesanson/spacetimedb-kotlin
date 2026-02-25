package dev.sanson.spacetimedb

import dev.sanson.spacetimedb.protocol.ClientMessage
import dev.sanson.spacetimedb.protocol.QuerySetId
import dev.sanson.spacetimedb.protocol.UnsubscribeFlags
import kotlinx.coroutines.channels.SendChannel
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A handle to an active or pending subscription.
 *
 * Tracks the subscription lifecycle: Sent → Applied → Ended/Error.
 * The [ClientMessage.Subscribe] is sent immediately when the handle is created
 * via [SubscriptionBuilder.subscribe].
 */
public class SubscriptionHandle internal constructor(
    internal val querySetId: QuerySetId,
    internal val querySql: List<String>,
    private val sendChannel: SendChannel<ClientMessage>,
    private var onApplied: (() -> Unit)?,
    private var onError: ((String) -> Unit)?,
) {
    @kotlin.concurrent.Volatile
    private var status: SubscriptionStatus = SubscriptionStatus.Sent

    @kotlin.concurrent.Volatile
    private var unsubscribeCalled = false
    private var onEnded: (() -> Unit)? = null

    /** True when the subscription is applied and not pending unsubscribe. */
    public val isActive: Boolean get() = status == SubscriptionStatus.Applied && !unsubscribeCalled

    /** True when the subscription has ended (unsubscribed or errored). */
    public val isEnded: Boolean get() = status == SubscriptionStatus.Ended || status == SubscriptionStatus.Error

    /**
     * Request unsubscription from this query set.
     *
     * @param onEnded Optional callback invoked when the server confirms the unsubscription.
     */
    @OptIn(ExperimentalAtomicApi::class)
    public fun unsubscribe(onEnded: (() -> Unit)? = null) {
        if (unsubscribeCalled || isEnded) return
        unsubscribeCalled = true
        this.onEnded = onEnded

        if (status == SubscriptionStatus.Applied) {
            val msg = ClientMessage.Unsubscribe(
                requestId = nextRequestId(),
                querySetId = querySetId,
                flags = UnsubscribeFlags.Default,
            )
            sendChannel.trySend(msg)
        }
    }

    /**
     * Called by the message loop when the server confirms the subscription (SubscribeApplied).
     * Transitions Sent → Applied and returns the onApplied callback.
     */
    internal fun notifyApplied(): (() -> Unit)? {
        if (status != SubscriptionStatus.Sent) return null
        status = SubscriptionStatus.Applied
        val callback = onApplied
        onApplied = null
        return callback
    }

    /**
     * Called by the message loop when the server confirms unsubscription (UnsubscribeApplied).
     * Transitions to Ended and returns the onEnded callback.
     */
    internal fun notifyEnded(): (() -> Unit)? {
        status = SubscriptionStatus.Ended
        val callback = onEnded
        onEnded = null
        return callback
    }

    /**
     * Called by the message loop when the server reports a subscription error.
     * Transitions to Error and returns the onError callback with the error message.
     */
    internal fun notifyError(error: String): (() -> Unit)? {
        status = SubscriptionStatus.Error
        val errorCallback = onError
        onError = null
        return if (errorCallback != null) {
            { errorCallback(error) }
        } else {
            null
        }
    }
}

internal enum class SubscriptionStatus {
    Sent,
    Applied,
    Ended,
    Error,
}

@OptIn(ExperimentalAtomicApi::class)
private val requestIdCounter = AtomicInt(1)

@OptIn(ExperimentalAtomicApi::class)
internal fun nextRequestId(): UInt = requestIdCounter.fetchAndAdd(1).toUInt()

@OptIn(ExperimentalAtomicApi::class)
private val querySetIdCounter = AtomicInt(1)

@OptIn(ExperimentalAtomicApi::class)
internal fun nextQuerySetId(): QuerySetId = QuerySetId(querySetIdCounter.fetchAndAdd(1).toUInt())
