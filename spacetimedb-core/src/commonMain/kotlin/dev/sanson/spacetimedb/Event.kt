package dev.sanson.spacetimedb

import dev.drewhamilton.poko.Poko

/**
 * A change in the state of a database connection which causes callbacks to run.
 *
 * Mirrors the Rust SDK's `Event` enum. The type parameter [R] represents the module-specific
 * reducer enum, defined by codegen.
 */
public abstract class Event<out R> private constructor() {

    /** A reducer this client invoked ran to completion and its mutations were committed. */
    @Poko public class Reducer<out R>(public val event: ReducerEvent<R>) : Event<R>()

    /** One of our subscriptions was applied. */
    public data object SubscribeApplied : Event<Nothing>()

    /** One of our subscriptions was removed. */
    public data object UnsubscribeApplied : Event<Nothing>()

    /** A subscription was ended by a disconnection. */
    public data object Disconnected : Event<Nothing>()

    /** An error caused one or more subscriptions to end prematurely or never be started. */
    @Poko public class SubscribeError(public val error: SpacetimeError) : Event<Nothing>()

    /** A transaction occurred in the remote module that was not invoked by this client. */
    public data object Transaction : Event<Nothing>()
}

/**
 * A state change due to a reducer, which may or may not have committed successfully.
 *
 * Mirrors the Rust SDK's `ReducerEvent` struct. The type parameter [R] represents the
 * module-specific reducer enum, defined by codegen.
 */
@Poko
public class ReducerEvent<out R>(
    /** The time at which the reducer was invoked. */
    public val timestamp: Timestamp,

    /** Whether the reducer committed, rolled back, or was aborted. */
    public val status: Status,

    /** Which reducer ran and its arguments. */
    public val reducer: R,
)

/**
 * The termination status of a [ReducerEvent].
 *
 * Mirrors the Rust SDK's `Status` enum.
 */
public abstract class Status private constructor() {

    /** The reducer terminated successfully and its mutations were committed. */
    public data object Committed : Status()

    /**
     * The reducer returned or threw a handleable error and its mutations were rolled back.
     *
     * @param message The error message signaled by the reducer.
     */
    @Poko public class Failed(public val message: String) : Status()

    /**
     * The reducer was aborted due to an unexpected or exceptional circumstance.
     *
     * @param message A description of the internal error.
     */
    @Poko public class Panic(public val message: String) : Status()
}
