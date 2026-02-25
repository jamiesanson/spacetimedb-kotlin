package dev.sanson.spacetimedb

/**
 * Errors that can occur in the SpacetimeDB client SDK.
 *
 * Mirrors the Rust SDK's `Error` enum.
 */
public abstract class SpacetimeError private constructor(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * The connection is already disconnected or has terminated normally.
     */
    public data object Disconnected : SpacetimeError("Connection is already disconnected or has terminated normally")

    /**
     * Failed to establish a connection to the SpacetimeDB host.
     */
    public class FailedToConnect(cause: Throwable? = null) :
        SpacetimeError("Failed to connect", cause)

    /**
     * The host returned an error when processing a subscription query.
     */
    public class SubscriptionError(public val error: String) :
        SpacetimeError("Host returned error when processing subscription query: $error")

    /**
     * The subscription has already ended and cannot be used.
     */
    public data object AlreadyEnded : SpacetimeError("Subscription has already ended")

    /**
     * Unsubscribe was already called on this subscription.
     */
    public data object AlreadyUnsubscribed : SpacetimeError("Unsubscribe already called on subscription")

    /**
     * An internal or unexpected error occurred in the SDK.
     */
    public class Internal(message: String, cause: Throwable? = null) :
        SpacetimeError(message, cause)
}
