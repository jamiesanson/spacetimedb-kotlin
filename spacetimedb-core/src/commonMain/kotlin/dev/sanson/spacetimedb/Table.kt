package dev.sanson.spacetimedb

/**
 * A read-only view of a table's cached rows, with callback registration for row changes.
 *
 * Implementations are generated per-table by the SpacetimeDB code generator. Each generated
 * table class delegates [count] and [iter] to the underlying [ClientCache][dev.sanson.spacetimedb.ClientCache],
 * and callback methods to the connection's callback registry.
 */
public interface Table<Row : Any> {

    /** Number of rows currently in the client cache for this table. */
    public val count: Int

    /** Iterate over all cached rows. */
    public fun iter(): Iterator<Row>

    /**
     * Register a callback invoked whenever a row is inserted into this table.
     *
     * @return a [CallbackId] handle that can be passed to [removeOnInsert] to deregister.
     */
    public fun onInsert(callback: (event: Event<*>, row: Row) -> Unit): CallbackId

    /** Remove a previously registered insert callback. */
    public fun removeOnInsert(id: CallbackId)

    /**
     * Register a callback invoked whenever a row is deleted from this table.
     *
     * @return a [CallbackId] handle that can be passed to [removeOnDelete] to deregister.
     */
    public fun onDelete(callback: (event: Event<*>, row: Row) -> Unit): CallbackId

    /** Remove a previously registered delete callback. */
    public fun removeOnDelete(id: CallbackId)
}

/**
 * A [Table] with a primary key column, which additionally supports update callbacks.
 *
 * An "update" is a paired delete + insert within the same transaction where the old and new
 * rows share the same primary key value.
 */
public interface TableWithPrimaryKey<Row : Any> : Table<Row> {

    /**
     * Register a callback invoked whenever a row is updated (delete + insert with same PK).
     *
     * @return a [CallbackId] handle that can be passed to [removeOnUpdate] to deregister.
     */
    public fun onUpdate(callback: (event: Event<*>, oldRow: Row, newRow: Row) -> Unit): CallbackId

    /** Remove a previously registered update callback. */
    public fun removeOnUpdate(id: CallbackId)
}

/**
 * A transient event-only table. Rows are never cached; only insert callbacks are supported.
 *
 * [count] always returns 0 and [iter] always returns an empty iterator.
 */
public interface EventTable<Row : Any> {

    /** Always returns 0 — event tables do not persist rows. */
    public val count: Int get() = 0

    /** Always returns an empty iterator — event tables do not persist rows. */
    public fun iter(): Iterator<Row> = emptyList<Row>().iterator()

    /**
     * Register a callback invoked whenever an event row is received.
     *
     * @return a [CallbackId] handle that can be passed to [removeOnEvent] to deregister.
     */
    public fun onEvent(callback: (event: Event<*>, row: Row) -> Unit): CallbackId

    /** Remove a previously registered event callback. */
    public fun removeOnEvent(id: CallbackId)
}
