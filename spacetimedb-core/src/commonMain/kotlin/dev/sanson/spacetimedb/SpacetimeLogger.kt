package dev.sanson.spacetimedb

/**
 * Logging interface for SpacetimeDB SDK internals.
 *
 * Implement this interface to receive log messages from the SDK.
 * By default, the SDK uses [NoOpLogger] which discards all messages.
 *
 * Example:
 * ```kotlin
 * val connection = SpacetimeDbConnection.builder()
 *     .withLogger(PrintLogger())
 *     .withUri("http://localhost:3000")
 *     .build(scope)
 * ```
 */
public interface SpacetimeLogger {
    public fun debug(message: String)
    public fun info(message: String)
    public fun warn(message: String, throwable: Throwable? = null)
    public fun error(message: String, throwable: Throwable? = null)
}

/**
 * Logger that discards all messages. This is the default.
 */
public object NoOpLogger : SpacetimeLogger {
    override fun debug(message: String) {}
    override fun info(message: String) {}
    override fun warn(message: String, throwable: Throwable?) {}
    override fun error(message: String, throwable: Throwable?) {}
}

/**
 * Simple logger that prints to stdout/stderr.
 *
 * Useful for quick debugging during development.
 */
public class PrintLogger(
    private val minLevel: Level = Level.Debug,
) : SpacetimeLogger {
    public enum class Level { Debug, Info, Warn, Error }

    override fun debug(message: String) {
        if (minLevel <= Level.Debug) println("[SpacetimeDB DEBUG] $message")
    }

    override fun info(message: String) {
        if (minLevel <= Level.Info) println("[SpacetimeDB INFO] $message")
    }

    override fun warn(message: String, throwable: Throwable?) {
        if (minLevel <= Level.Warn) {
            println("[SpacetimeDB WARN] $message")
            throwable?.let { println("  Cause: $it") }
        }
    }

    override fun error(message: String, throwable: Throwable?) {
        if (minLevel <= Level.Error) {
            println("[SpacetimeDB ERROR] $message")
            throwable?.let { println("  Cause: $it") }
        }
    }
}
