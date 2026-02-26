package dev.sanson.spacetimedb

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for automatic reconnection with exponential backoff.
 *
 * When the WebSocket connection drops unexpectedly (network change, server restart, etc.),
 * the SDK will automatically attempt to reconnect using the configured parameters.
 *
 * User-initiated disconnects via [SpacetimeDbConnection.disconnect] are never retried.
 *
 * @property maxAttempts Maximum number of reconnection attempts before giving up.
 * @property initialDelay Delay before the first reconnection attempt.
 * @property maxDelay Maximum delay between reconnection attempts (caps exponential growth).
 */
public class ReconnectConfig(
    public val maxAttempts: Int = 5,
    public val initialDelay: Duration = 1.seconds,
    public val maxDelay: Duration = 30.seconds,
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive, was $maxAttempts" }
        require(!initialDelay.isNegative()) { "initialDelay must not be negative, was $initialDelay" }
        require(maxDelay >= initialDelay) { "maxDelay ($maxDelay) must be >= initialDelay ($initialDelay)" }
    }
}
