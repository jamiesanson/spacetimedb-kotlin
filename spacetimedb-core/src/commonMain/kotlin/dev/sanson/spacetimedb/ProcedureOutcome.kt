package dev.sanson.spacetimedb

/**
 * The outcome of a procedure call.
 *
 * Procedures are non-transactional server functions that return values directly, unlike reducers
 * which produce side effects via subscription updates.
 *
 * @see SpacetimeDbConnection.callProcedure
 */
public abstract class ProcedureOutcome private constructor() {

    /** The server-side timestamp when the procedure executed. */
    public abstract val timestamp: Timestamp

    /** Total host execution duration for the procedure. */
    public abstract val executionDuration: TimeDuration

    /**
     * The procedure returned a value successfully.
     *
     * @property value The BSATN-encoded return value. Use the appropriate kotlinx.serialization
     *   deserializer (or generated helpers) to decode.
     */
    public class Returned
    internal constructor(
        public val value: ByteArray,
        override val timestamp: Timestamp,
        override val executionDuration: TimeDuration,
    ) : ProcedureOutcome() {
        override fun toString(): String =
            "ProcedureOutcome.Returned(${value.size} bytes, timestamp=$timestamp)"
    }

    /** The procedure failed with a server-side internal error. */
    public class InternalError
    internal constructor(
        public val message: String,
        override val timestamp: Timestamp,
        override val executionDuration: TimeDuration,
    ) : ProcedureOutcome() {
        override fun toString(): String =
            "ProcedureOutcome.InternalError($message, timestamp=$timestamp)"
    }
}
