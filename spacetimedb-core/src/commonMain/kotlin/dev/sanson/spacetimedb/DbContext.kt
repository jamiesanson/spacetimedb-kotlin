package dev.sanson.spacetimedb

/**
 * Generic context for interacting with a SpacetimeDB module.
 *
 * Generated per-module `DbConnection` classes implement this interface with concrete [Tables] and
 * [Reducers] types, providing typed access to the module's tables and reducers.
 *
 * @param Tables the generated `RemoteTables` interface for this module
 * @param Reducers the generated `RemoteReducers` interface for this module
 */
public interface DbContext<Tables, Reducers> {
    /** Typed access to the module's table handles. */
    public val db: Tables

    /** Typed access to the module's reducer invocations. */
    public val reducers: Reducers

    /** The identity of this client, or `null` if not yet connected. */
    public val identity: Identity?

    /** The connection ID, or `null` if not yet connected. */
    public val connectionId: ConnectionId?

    /** Whether the underlying WebSocket connection is active. */
    public val isActive: Boolean

    /** Create a new [SubscriptionBuilder] for subscribing to queries. */
    public fun subscriptionBuilder(): SubscriptionBuilder

    /** Disconnect from the SpacetimeDB instance. */
    public fun disconnect()
}
